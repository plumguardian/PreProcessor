package dev.tocraft.gradle.preprocess;

import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.SourceDirectories;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import dev.tocraft.gradle.preprocess.data.PreprocessExtension;
import dev.tocraft.gradle.preprocess.tasks.ApplyPreProcessTask;
import dev.tocraft.gradle.preprocess.tasks.PreProcessTask;

public final class AndroidPreprocessorIntegration {
    private AndroidPreprocessorIntegration() {}

    public static void configure(@NotNull Project project, PreprocessExtension ext) {
        ApplicationAndroidComponentsExtension androidComponents =
                project.getExtensions().getByType(
                        ApplicationAndroidComponentsExtension.class
                );

        androidComponents.onVariants(
                androidComponents.selector().all(),
                variant -> {
                    String variantName = variant.getName();

                    String capitalizedVariantName =
                            Character.toUpperCase(variantName.charAt(0))
                                    + variantName.substring(1);

                    TaskProvider<PreProcessTask> preprocessJava =
                            project.getTasks().register(
                                    "preprocess" + capitalizedVariantName + "Java",
                                    PreProcessTask.class,
                                    task -> {
                                        task.getVars().convention(ext.vars);
                                        task.getRemap().convention(ext.remapper);
                                        task.getKeywords().convention(ext.keywords);

                                        task.getSources().set(List.of(
                                                project.file("src/main/java")
                                        ));

                                        task.getTarget().set(
                                                project.getLayout().getBuildDirectory()
                                                        .dir("generated/preprocessed/" +
                                                                variantName + "/java")
                                        );

                                        task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
                                        task.getFileExtension().set(".java");

                                        // TODO: remove once cache is tested more
                                        task.getOutputs().upToDateWhen(t -> false);
                                    }
                            );

                    SourceDirectories.Flat javaSources = variant.getSources().getJava();

                    if (javaSources == null) {
                        throw new IllegalStateException(
                                "Java sources unavailable for Android variant: " + variant.getName()
                        );
                    }

                    javaSources.addGeneratedSourceDirectory(
                            preprocessJava,
                            PreProcessTask::getTarget
                    );

                    TaskProvider<ApplyPreProcessTask> applyPreProcessJava =
                            project.getTasks().register(
                                    "applyPreProcess" + capitalizedVariantName + "Java",
                                    ApplyPreProcessTask.class,
                                    preprocessJava
                            );

                    applyPreProcessJava.configure(task -> {
                        task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
                        task.getFileExtension().set(".java");
                        task.dependsOn(preprocessJava);
                    });

                    // TODO: kotlin optional
                    TaskProvider<PreProcessTask> preprocessKotlin =
                            project.getTasks().register(
                                    "preprocess" + capitalizedVariantName + "Kotlin",
                                    PreProcessTask.class,
                                    task -> {
                                        task.getVars().convention(ext.vars);
                                        task.getRemap().convention(ext.remapper);
                                        task.getKeywords().convention(ext.keywords);

                                        task.getSources().set(List.of(
                                                project.file("src/main/java")
                                        ));

                                        task.getTarget().set(
                                                project.getLayout().getBuildDirectory()
                                                        .dir("generated/preprocessed/"
                                                                + variantName + "/kotlin")
                                        );

                                        task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
                                        task.getFileExtension().set(".kt");
                                        task.getOutputs().upToDateWhen(t -> false);
                                    }
                            );

                    SourceDirectories.Flat kotlinSources = variant.getSources().getKotlin();

                    if (kotlinSources == null) {
                        throw new IllegalStateException(
                                "Kotlin sources unavailable for Android variant: " + variant.getName()
                        );
                    }

                    kotlinSources.addGeneratedSourceDirectory(
                            preprocessKotlin,
                            PreProcessTask::getTarget
                    );

                    TaskProvider<ApplyPreProcessTask> applyPreProcessKotlin =
                            project.getTasks().register(
                                    "applyPreProcess" + capitalizedVariantName + "Kotlin",
                                    ApplyPreProcessTask.class,
                                    preprocessKotlin
                            );

                    applyPreProcessKotlin.configure(task -> {
                        task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
                        task.getFileExtension().set(".kt");
                        task.dependsOn(preprocessKotlin);
                    });
                }
        );
    }
}
