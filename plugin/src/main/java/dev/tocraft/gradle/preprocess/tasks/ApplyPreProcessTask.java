package dev.tocraft.gradle.preprocess.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import dev.tocraft.gradle.preprocess.data.Keywords;
import dev.tocraft.gradle.preprocess.util.PreProcessor;
import dev.tocraft.gradle.preprocess.util.ReMapper;

/**
 * Task to overwrite the original source files with the results of the {@link PreProcessTask}
 */
public class ApplyPreProcessTask extends DefaultTask {
    private final Property<Boolean> removeComments;
    private final MapProperty<String, Object> vars;
    private final MapProperty<String, String> remap;
    private final MapProperty<String, Keywords> keywords;
    private final ListProperty<File> targets;
    private final ConfigurableFileCollection comingFiles;
    private final DirectoryProperty projectDirectory;
    private final Property<String> fileExtension;

    /**
     * @param factory        some object factory to crate the properties
     * @param preProcessTask the delgate preprocess task to be used
     */
    @Inject
    public ApplyPreProcessTask(final @NotNull ObjectFactory factory, final @NotNull TaskProvider<PreProcessTask> preProcessTask) {
        this.removeComments = factory.property(Boolean.class).convention(false);

        this.targets = factory.listProperty(File.class).convention(preProcessTask.flatMap(PreProcessTask::getSources));

        this.vars = factory.mapProperty(String.class, Object.class).convention(preProcessTask.flatMap(PreProcessTask::getVars));
        this.remap = factory.mapProperty(String.class, String.class).convention(preProcessTask.flatMap(PreProcessTask::getRemap));
        this.keywords = factory.mapProperty(String.class, Keywords.class).convention(preProcessTask.flatMap(PreProcessTask::getKeywords));

        this.comingFiles = factory.fileCollection();

        this.projectDirectory = factory.directoryProperty();
        this.fileExtension = factory.property(String.class).convention("");
    }

    /**
     * @return if the preprocess task will remove commented preprocessor commands
     */
    @Internal
    public Property<Boolean> getRemoveComments() {
        return removeComments;
    }

    /**
     * @return target folder where the sources files should be overwritten
     */
    @InputFiles
    public ListProperty<File> getTargets() {
        return targets;
    }

    @Input
    public Property<String> getFileExtension() {
        return fileExtension;
    }

    /**
     * @return the overwritten files
     */
    @OutputFiles
    public FileCollection getComingFiles() {
        return this.comingFiles;
    }

    @Internal
    @Override
    public String getDescription() {
        return "PreProcess files.";
    }

    @Internal
    public DirectoryProperty getProjectDirectory() {
        return projectDirectory;
    }

    /**
     * The actual task action
     */
    @TaskAction
    public void applyPreProcess() {
        Set<File> foundFiles = new HashSet<>();

        PreProcessor preProcessor = new PreProcessor(removeComments.get(), vars.get(), keywords.get());
        ReMapper reMapper = new ReMapper(remap.get());

        // place file in their original source folder
        for (File srcFolder : targets.get()) {
            final Path outBasePath = srcFolder.isAbsolute()
                    ? srcFolder.toPath()
                    : projectDirectory.get().getAsFile().toPath()
                    .resolve(srcFolder.toPath())
                    .normalize();

            if (!Files.isDirectory(outBasePath)) {
                getLogger().info(
                        "Skipping nonexistent source directory: {}",
                        outBasePath
                );
                continue;
            }

            // iterate over the existing files in the targets folders so the preprocessed files can be copied to their exact source folder
            // might be buggy when interfered by externals
            try (var paths = Files.walk(outBasePath)) {
                paths.filter(Files::isRegularFile).filter(
                        path -> fileExtension.get().isEmpty()
                                || path.getFileName().toString()
                                .endsWith(fileExtension.get())
                ).forEach(path -> {
                    File file = path.toFile();

                    // old school preprocessing
                    preProcessor.convertFile(reMapper, file, file);

                    foundFiles.add(file);
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to preprocess files in " + outBasePath, e);
            }
        }

        this.comingFiles.setFrom(foundFiles);
    }
}
