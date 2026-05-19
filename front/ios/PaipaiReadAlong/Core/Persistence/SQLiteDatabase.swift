import Foundation
import SQLite3

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

protocol SQLiteCursorProtocol {
    func getString(name: String) throws -> String
    func getStringOptional(name: String) throws -> String?
    func getIntOptional(name: String) throws -> Int?
    func getInt64Optional(name: String) throws -> Int64?
    func getBooleanOptional(name: String) throws -> Bool?
}

final class SQLiteDatabaseCursor: SQLiteCursorProtocol {
    private let statement: OpaquePointer?
    private let columnIndexByName: [String: Int32]

    init(statement: OpaquePointer?) {
        self.statement = statement
        var map: [String: Int32] = [:]
        let count = sqlite3_column_count(statement)
        for index in 0..<count {
            if let raw = sqlite3_column_name(statement, index) {
                map[String(cString: raw)] = index
            }
        }
        self.columnIndexByName = map
    }

    func getString(name: String) throws -> String {
        guard let value = try getStringOptional(name: name) else {
            throw SQLiteDatabaseError.missingColumn(name)
        }
        return value
    }

    func getStringOptional(name: String) throws -> String? {
        guard let index = columnIndexByName[name] else { return nil }
        if sqlite3_column_type(statement, index) == SQLITE_NULL { return nil }
        guard let raw = sqlite3_column_text(statement, index) else { return nil }
        return String(cString: raw)
    }

    func getIntOptional(name: String) throws -> Int? {
        guard let index = columnIndexByName[name] else { return nil }
        if sqlite3_column_type(statement, index) == SQLITE_NULL { return nil }
        return Int(sqlite3_column_int64(statement, index))
    }

    func getInt64Optional(name: String) throws -> Int64? {
        guard let index = columnIndexByName[name] else { return nil }
        if sqlite3_column_type(statement, index) == SQLITE_NULL { return nil }
        return sqlite3_column_int64(statement, index)
    }

    func getBooleanOptional(name: String) throws -> Bool? {
        guard let value = try getIntOptional(name: name) else { return nil }
        return value != 0
    }
}

enum SQLiteDatabaseError: Error {
    case openFailed(String)
    case prepareFailed(String)
    case executeFailed(String)
    case missingColumn(String)
}

final class LocalDatabase {
    private let dbPath: String
    private let lock = NSLock()

    init(dbFilename: String) {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        dbPath = directory.appendingPathComponent(dbFilename).path
        try? openAndBootstrap()
    }

    func getAll<T>(sql: String, parameters: [Any?], map: (SQLiteCursorProtocol) throws -> T) async throws -> [T] {
        try query(sql: sql, parameters: parameters, map: map, one: false)
    }

    func getOptional<T>(sql: String, parameters: [Any?], map: (SQLiteCursorProtocol) throws -> T) async throws -> T? {
        try query(sql: sql, parameters: parameters, map: map, one: true).first
    }

    func execute(sql: String, parameters: [Any?]) async throws {
        try withStatement(sql: sql, parameters: parameters) { statement, db in
            if sqlite3_step(statement) != SQLITE_DONE {
                throw SQLiteDatabaseError.executeFailed(message(for: db))
            }
        }
    }

    private func query<T>(sql: String, parameters: [Any?], map: (SQLiteCursorProtocol) throws -> T, one: Bool) throws -> [T] {
        try withStatement(sql: sql, parameters: parameters) { statement, db in
            var results: [T] = []
            while true {
                let code = sqlite3_step(statement)
                if code == SQLITE_ROW {
                    results.append(try map(SQLiteDatabaseCursor(statement: statement)))
                    if one { break }
                } else if code == SQLITE_DONE {
                    break
                } else {
                    throw SQLiteDatabaseError.executeFailed(message(for: db))
                }
            }
            return results
        }
    }

    private func withStatement<T>(sql: String, parameters: [Any?], body: (OpaquePointer?, OpaquePointer?) throws -> T) throws -> T {
        lock.lock()
        defer { lock.unlock() }
        var db: OpaquePointer?
        var statement: OpaquePointer?
        guard sqlite3_open(dbPath, &db) == SQLITE_OK else {
            throw SQLiteDatabaseError.openFailed(message(for: db))
        }
        defer { sqlite3_close(db) }
        try executeBootstrap(on: db)
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw SQLiteDatabaseError.prepareFailed(message(for: db))
        }
        defer { sqlite3_finalize(statement) }
        try bind(parameters, to: statement)
        return try body(statement, db)
    }

    private func bind(_ parameters: [Any?], to statement: OpaquePointer?) throws {
        for (index, value) in parameters.enumerated() {
            let position = Int32(index + 1)
            switch value {
            case nil:
                sqlite3_bind_null(statement, position)
            case let value as Int:
                sqlite3_bind_int64(statement, position, Int64(value))
            case let value as Int64:
                sqlite3_bind_int64(statement, position, value)
            case let value as Bool:
                sqlite3_bind_int64(statement, position, value ? 1 : 0)
            case let value as Double:
                sqlite3_bind_double(statement, position, value)
            case let value as String:
                sqlite3_bind_text(statement, position, value, -1, sqliteTransient)
            case let value as NSDate:
                sqlite3_bind_text(statement, position, value.description, -1, sqliteTransient)
            default:
                sqlite3_bind_text(statement, position, String(describing: value!), -1, sqliteTransient)
            }
        }
    }

    private func openAndBootstrap() throws {
        var handle: OpaquePointer?
        guard sqlite3_open(dbPath, &handle) == SQLITE_OK else {
            throw SQLiteDatabaseError.openFailed(message(for: handle))
        }
        defer { sqlite3_close(handle) }
        try executeBootstrap(on: handle)
    }

    private func executeBootstrap(on db: OpaquePointer?) throws {
        for statement in SQLiteSchema.bootstrapStatements {
            if sqlite3_exec(db, statement, nil, nil, nil) != SQLITE_OK {
                throw SQLiteDatabaseError.executeFailed(message(for: db))
            }
        }
    }

    private func message(for db: OpaquePointer?) -> String {
        if let message = sqlite3_errmsg(db) {
            return String(cString: message)
        }
        return "sqlite error"
    }
}
