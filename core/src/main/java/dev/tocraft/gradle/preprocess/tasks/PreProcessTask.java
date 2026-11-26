package dev.tocraft.gradle.preprocess.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import dev.tocraft.gradle.preprocess.data.Keywords;
import dev.tocraft.gradle.preprocess.util.PreProcessor;
import dev.tocraft.gradle.preprocess.util.ReMapper;

/**
 * Task to overwrite the original source files with the processed files
 */
public class PreProcessTask extends DefaultTask {
    private final Property<Boolean> removeComments;
    private final MapProperty<String, Object> vars;
    private final MapProperty<String, String> remap;
    private final MapProperty<String, Keywords> keywords;
    private final ListProperty<File> targets;
    private final DirectoryProperty output;
    private final DirectoryProperty projectDirectory;
    private final FileSystemOperations fileSystemOperations;

    /**
     * @param factory some object factory to crate the properties
     */
    @Inject
    public PreProcessTask(@NotNull ObjectFactory factory, @NotNull FileSystemOperations fileSystemOperations) {
        this.removeComments = factory.property(Boolean.class).convention(false);

        this.targets = factory.listProperty(File.class);

        this.vars = factory.mapProperty(String.class, Object.class);
        this.remap = factory.mapProperty(String.class, String.class);
        this.keywords = factory.mapProperty(String.class, Keywords.class);

        this.output = factory.directoryProperty();
        this.projectDirectory = factory.directoryProperty();

        this.fileSystemOperations = fileSystemOperations;
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
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public ListProperty<File> getTargets() {
        return targets;
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

    @OutputDirectory
    public DirectoryProperty getOutput() {
        return output;
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

    /**
     * The actual task action
     */
    @TaskAction
    public void applyPreProcess() {
        PreProcessor preProcessor = new PreProcessor(
                removeComments.get(),
                vars.get(),
                keywords.get()
        );
        ReMapper reMapper = new ReMapper(remap.get());
        fileSystemOperations.delete(spec -> spec.delete(output.get()));

        Path projectPath = projectDirectory.get().getAsFile()
                .toPath().toAbsolutePath().normalize();

        Path outputPath = output.get().getAsFile()
                .toPath().toAbsolutePath().normalize();

        // place file in their original source folder
        for (File srcFolder : targets.get()) {
            Path inBasePath = (srcFolder.isAbsolute()
                    ? srcFolder.toPath()
                    : projectPath.resolve(srcFolder.toPath()))
                    .toAbsolutePath()
                    .normalize();

            if (!inBasePath.startsWith(projectPath)) {
                getLogger().warn(
                        "Source directory is outside the project directory, skipping: {}",
                        inBasePath
                );
                continue;
            }

            if (!Files.isDirectory(inBasePath)) {
                getLogger().warn(
                        "Skipping nonexistent source directory: {}",
                        inBasePath
                );
                continue;
            }

            // iterate over the existing files in the targets folders so the preprocessed files can be copied to their exact source folder
            // might be buggy when interfered by externals
            try (var paths = Files.walk(inBasePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.toAbsolutePath().normalize()
                                .startsWith(outputPath))
                        .forEach(inPath -> {
                    try {
                        preProcessor.convertFile(
                                reMapper, inPath,
                                outputPath.resolve(projectPath.relativize(inPath))
                        );
                    } catch (IOException e) {
                        getLogger().warn(
                                "Failed to preprocess file '{}'",
                                inPath, e
                        );
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to preprocess files in " + inBasePath, e);
            }
        }
    }
}
