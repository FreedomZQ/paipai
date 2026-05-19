package com.apphub.backend.common.db;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 `FlywayMigrationNaming` 的测试类。
 * 用于验证核心业务逻辑、配置约束或关键边界条件。
 */

class FlywayMigrationNamingTest {

    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void firstVersionShouldContainOnlyUnifiedBaseline() throws Exception {
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
        assertThat(versions).containsExactly(1);
        assertThat(Arrays.stream(files).map(File::getName).toList()).containsExactly("V1__init.sql");
    }

    private static Integer version(String filename) {
        Matcher matcher = MIGRATION_PATTERN.matcher(filename);
        assertThat(matcher.matches()).as("valid migration filename: %s", filename).isTrue();
        return Integer.parseInt(matcher.group(1));
    }
}
