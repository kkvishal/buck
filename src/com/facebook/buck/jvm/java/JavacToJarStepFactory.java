/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.jvm.java;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.core.SuggestBuildRules;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;

public class JavacToJarStepFactory extends BaseCompileToJarStepFactory {

  private final JavacOptions javacOptions;
  private final JavacOptionsAmender amender;

  public JavacToJarStepFactory(JavacOptions javacOptions, JavacOptionsAmender amender) {
    this.javacOptions = javacOptions;
    this.amender = amender;
  }

  @Override
  public void createCompileStep(
      BuildContext context,
      ImmutableSortedSet<Path> sourceFilePaths,
      BuildTarget invokingRule,
      SourcePathResolver resolver,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> declaredClasspathEntries,
      Path outputDirectory,
      Optional<Path> workingDirectory,
      Path pathToSrcsList,
      Optional<SuggestBuildRules> suggestBuildRules,
      ClassUsageFileWriter usedClassesFileWriter,
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {

    final JavacOptions buildTimeOptions = amender.amend(javacOptions, context);

    // Javac requires that the root directory for generated sources already exist.
    addAnnotationGenFolderStep(buildTimeOptions, filesystem, steps, buildableContext);

    steps.add(
        new JavacStep(
            outputDirectory,
            usedClassesFileWriter,
            Optional.<StandardJavaFileManagerFactory>absent(),
            workingDirectory,
            sourceFilePaths,
            pathToSrcsList,
            declaredClasspathEntries,
            buildTimeOptions.getJavac(),
            buildTimeOptions,
            invokingRule,
            suggestBuildRules,
            resolver,
            filesystem));
  }

  @Override
  public void createCompileToJarStep(
      BuildContext context,
      ImmutableSortedSet<Path> sourceFilePaths,
      BuildTarget invokingRule,
      SourcePathResolver resolver,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> declaredClasspathEntries,
      Path outputDirectory,
      Optional<Path> workingDirectory,
      Path pathToSrcsList,
      Optional<SuggestBuildRules> suggestBuildRules,
      ImmutableList<String> postprocessClassesCommands,
      ImmutableSortedSet<Path> entriesToJar,
      Optional<String> mainClass,
      Optional<Path> manifestFile,
      Path outputJar,
      ClassUsageFileWriter usedClassesFileWriter,
      /* output params */
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {

    boolean spoolToJar =
        postprocessClassesCommands.isEmpty() &&
            javacOptions.getSpoolMode() == AbstractJavacOptions.SpoolMode.DIRECT_TO_JAR &&
            javacOptions.getJavac() instanceof Jsr199Javac;

    if (!spoolToJar) {
      super.createCompileToJarStep(
          context,
          sourceFilePaths,
          invokingRule,
          resolver,
          filesystem,
          declaredClasspathEntries,
          outputDirectory,
          workingDirectory,
          pathToSrcsList,
          suggestBuildRules,
          postprocessClassesCommands,
          entriesToJar,
          mainClass,
          manifestFile,
          outputJar,
          usedClassesFileWriter,
          steps,
          buildableContext);
      return;
    }

    final JavacOptions buildTimeOptions = amender.amend(javacOptions, context);

    // Javac requires that the root directory for generated sources already exist.
    addAnnotationGenFolderStep(buildTimeOptions, filesystem, steps, buildableContext);

    steps.add(
        new JavacDirectToJarStep(
            sourceFilePaths,
            invokingRule,
            resolver,
            filesystem,
            declaredClasspathEntries,
            buildTimeOptions,
            outputDirectory,
            workingDirectory,
            pathToSrcsList,
            suggestBuildRules,
            entriesToJar,
            mainClass,
            manifestFile,
            outputJar,
            usedClassesFileWriter));
  }

  private static void addAnnotationGenFolderStep(
      JavacOptions buildTimeOptions,
      ProjectFilesystem filesystem,
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {
    Optional<Path> annotationGenFolder =
        buildTimeOptions.getGeneratedSourceFolderName();
    if (annotationGenFolder.isPresent()) {
      steps.add(new MakeCleanDirectoryStep(filesystem, annotationGenFolder.get()));
      buildableContext.recordArtifact(annotationGenFolder.get());
    }
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("javacOptions", javacOptions);
    sink.setReflectively("amender", amender);
  }
}
