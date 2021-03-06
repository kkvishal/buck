/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.util;

import static com.facebook.buck.io.MorePaths.pathWithPlatformSeparators;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.io.DirectoryTraversal;
import com.facebook.buck.io.MorePaths;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;

public class LicenseCheckTest {

  // Files where we're okay with the license not being the normal Facebook apache one. We also
  // exclude all files under "test/**/testdata/"
  private static final Set<String> NON_APACHE_LICENSE_WHITELIST = ImmutableSet.of(
      // Because it's not originally our code.
      pathWithPlatformSeparators("com/facebook/buck/jvm/java/coverage/ReportGenerator.java"),
      pathWithPlatformSeparators("com/facebook/buck/util/WindowsCreateProcessEscape.java"),
      pathWithPlatformSeparators("com/facebook/buck/util/WindowsCreateProcessEscapeTest.java"));

  private static final String NON_APACHE_LICENSE_WHITELIST_DIR =
      pathWithPlatformSeparators("com/facebook/buck/cli/quickstart/android/");

  @Test
  public void ensureAllSrcFilesHaveTheApacheLicense() throws IOException {
    new JavaCopyrightTraversal(Paths.get("src"), false).traverse();
    new JavaCopyrightTraversal(Paths.get("test"), true).traverse();
  }

  private static class JavaCopyrightTraversal extends DirectoryTraversal {

    private static final Pattern LICENSE_FRAGMENT = Pattern.compile(
        // TODO(shs96c): This is very lame.
        // The newline character doesn't match "\w", "\\n" so do a non-greedy match until the next
        // part of the copyright.
        "^/\\\\*.*?" +
        "\\\\* Copyright 20\\d\\d-present Facebook, Inc\\..*?" +
        "\\\\* Licensed under the Apache License, Version 2.0 \\(the \"License\"\\); you may.*",
        Pattern.MULTILINE | Pattern.DOTALL);

    private static final Path TEST_DATA = Paths.get("testdata");

    private final boolean ignoreTestData;

    public JavaCopyrightTraversal(Path root, boolean ignoreTestData) {
      super(root);
      this.ignoreTestData = ignoreTestData;
    }

    @Override
    public void visit(Path file, String relativePath) {
      if (!"java".equals(MorePaths.getFileExtension(file)) ||
          // Ignore dangling symlinks.
          !Files.exists(file) ||
          NON_APACHE_LICENSE_WHITELIST.contains(relativePath) ||
          relativePath.startsWith(NON_APACHE_LICENSE_WHITELIST_DIR)) {
        return;
      }

      if (ignoreTestData) {
        for (Path path : file) {
           if (TEST_DATA.equals(path)) {
             return;
           }
        }
      }

      try {
        String asString = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue("Check license of: " + relativePath,
            LICENSE_FRAGMENT.matcher(asString).matches());
      } catch (IOException e) {
        fail("Unable to read: " + relativePath);
      }
    }
  }
}
