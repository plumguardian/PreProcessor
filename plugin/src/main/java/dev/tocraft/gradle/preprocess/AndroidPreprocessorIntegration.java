package dev.tocraft.gradle.preprocess;

import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.SourceDirectories;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import dev.tocraft.gradle.preprocess.data.PreprocessExtension;
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

                    TaskProvider<PreProcessTask> preprocessJava =
                            project.getTasks().register(
                                    "preprocess" +
                                            Character.toUpperCase(variantName.charAt(0)) +
                                            variantName.substring(1) + "Java",
                                    PreProcessTask.class,
                                    task -> {
                                        task.getVars().convention(ext.vars);
                                        task.getRemap().convention(ext.remapper);
                                        task.getKeywords().convention(ext.keywords);

                                        task.getSources().set(
                                                List.of(project.file("src/main/java"))
                                        );

                                        task.getTarget().set(
                                                project.getLayout().getBuildDirectory()
                                                        .dir("generated/preprocessed/" +
                                                                variantName + "/java")
                                        );
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
                }
        );
    }
}
