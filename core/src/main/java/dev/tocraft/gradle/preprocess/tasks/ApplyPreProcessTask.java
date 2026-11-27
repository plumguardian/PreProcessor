package dev.tocraft.gradle.preprocess.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dev.tocraft.gradle.preprocess.data.Keywords;
import dev.tocraft.gradle.preprocess.util.PreProcessor;
import dev.tocraft.gradle.preprocess.util.ReMapper;

/**
 * Task to overwrite the original source files with the processed files
 */
public class ApplyPreProcessTask extends DefaultTask {
    private final Property<Boolean> removeComments;
    private final MapProperty<String, Object> vars;
    private final MapProperty<String, String> remap;
    private final MapProperty<String, Keywords> keywords;
    private final ListProperty<File> targets;
    private final ConfigurableFileCollection comingFiles;
    private final DirectoryProperty projectDirectory;
    private final SetProperty<String> fileExtension;

    /**
     * @param factory some object factory to crate the properties
     */
    @Inject
    public ApplyPreProcessTask(@NotNull ObjectFactory factory) {
        this.removeComments = factory.property(Boolean.class).convention(false);

        this.targets = factory.listProperty(File.class);

        this.vars = factory.mapProperty(String.class, Object.class);
        this.remap = factory.mapProperty(String.class, String.class);
        this.keywords = factory.mapProperty(String.class, Keywords.class);

        this.comingFiles = factory.fileCollection();

        this.projectDirectory = factory.directoryProperty();

        this.fileExtension = factory.setProperty(String.class);
    }

    /**
     * @param preProcessTask the delgate preprocess task to be used
     */
    public void applyFrom(@NotNull TaskProvider<PreProcessTask> preProcessTask) {
        this.removeComments.convention(preProcessTask.flatMap(PreProcessTask::getRemoveComments));

        this.targets.convention(preProcessTask.flatMap(PreProcessTask::getTargets));

        this.vars.convention(preProcessTask.flatMap(PreProcessTask::getVars));
        this.remap.convention(preProcessTask.flatMap(PreProcessTask::getRemap));
        this.keywords.convention(preProcessTask.flatMap(PreProcessTask::getKeywords));

        this.projectDirectory.convention(preProcessTask.flatMap(PreProcessTask::getProjectDirectory));

        this.fileExtension.convention(preProcessTask.flatMap(PreProcessTask::getFileExtension));
    }

    /**
     * @return if the preprocess task will remove commented preprocessor commands
     */
    @Input
    public Property<Boolean> getRemoveComments() {
        return removeComments;
    }

    /**
     * @return target folder where the sources files should be overwritten
     */
    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public ListProperty<File> getTargets() {
        return targets;
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

    @Input
    public MapProperty<String, Object> getVars() {
        return vars;
    }

    @Input
    public MapProperty<String, String> getRemap() {
        return remap;
    }

    @Input
    public MapProperty<String, Keywords> getKeywords() {
        return keywords;
    }

    @Internal
    public DirectoryProperty getProjectDirectory() {
        return projectDirectory;
    }

    @Input
    public Provider<String> getProjectDirectoryPath() {
        return projectDirectory.map(dir ->
                dir.getAsFile().toPath().toAbsolutePath().normalize().toString()
        );
    }

    @Input
    public SetProperty<String> getFileExtension() {
        return fileExtension;
    }

    /**
     * The actual task action
     */
    @TaskAction
    public void applyPreProcess() {
        Set<Path> foundFiles = new HashSet<>();

        PreProcessor preProcessor = new PreProcessor(
                removeComments.get(),
                vars.get(),
                keywords.get()
        );
        ReMapper reMapper = new ReMapper(remap.get());

        Set<String> extensions = fileExtension.get().stream()
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // place file in their original source folder
        for (File srcFolder : targets.get()) {
            Path outBasePath = srcFolder.isAbsolute()
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
                var pathStream = paths.filter(Files::isRegularFile);

                if (!extensions.isEmpty()) {
                    pathStream = pathStream.filter(path -> {
                        String name = path.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        return dot >= 0 && extensions.contains(name.substring(dot).toLowerCase(Locale.ROOT));
                    });
                }

                pathStream.forEach(path -> {
                    try {
                        if (preProcessor.convertFileInPlace(reMapper, path)) {
                            foundFiles.add(path);
                        }
                    } catch (IOException e) {
                        getLogger().warn(
                                "Failed to preprocess file '{}'",
                                path, e
                        );
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to preprocess files in " + outBasePath, e);
            }
        }

        this.comingFiles.setFrom(foundFiles);
    }
}
