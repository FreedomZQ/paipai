package com.apphub.backend.common.db;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `FlywayMigrationNaming` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class FlywayMigrationNamingTest {

    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void migrationVersionsShouldBeContiguousAndUnique() throws Exception {
        URI migrationDir = Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader().getResource("db/first_version")
        ).toURI();
        File[] files = new File(migrationDir).listFiles((dir, name) -> name.startsWith("V") && name.endsWith(".sql"));
        assertThat(files).isNotNull();

        List<Integer> versions = Arrays.stream(files)
            .map(File::getName)
            .map(FlywayMigrationNamingTest::version)
            .sorted()
            .toList();

        assertThat(versions).isNotEmpty();
        assertThat(versions).doesNotHaveDuplicates();
        List<Integer> expected = IntStream.rangeClosed(1, versions.get(versions.size() - 1)).boxed().toList();
        List<Integer> missing = expected.stream().filter(version -> !versions.contains(version)).toList();
        // V1 是当前首发库的合并基线；V2~V38 是旧开发期迁移号段，已被合并进 V1 或随非拍拍模块清理删除。
        // 不补空迁移文件，避免已经执行过 V18 的环境在 Flyway 校验时出现“低版本未应用”的运维风险。
        // 新增迁移仍必须从当前最大版本继续递增，并且不得重复。
        assertThat(missing).containsExactlyElementsOf(IntStream.rangeClosed(2, 38).boxed().toList());
    }

    private static Integer version(String filename) {
        Matcher matcher = MIGRATION_PATTERN.matcher(filename);
        assertThat(matcher.matches()).as("valid migration filename: %s", filename).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
