package com.quentity.refGenPlug;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Mojo(name = "RefGenPlug", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class EntityFieldScannerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}/src/main/java")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/reflection")
    private File resourcesDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/META-INF/native-image")
    private File nativeImageDirectory;

    private static final String ENTITY_SUPERCLASS = "Entity";
    private static final String RES_SUPERCLASS = "Res";
    private Map<String, Set<String>> classHierarchy;
    private File reflectionFile;
    private File reflectConfigFile;
    private FilePojo.FilePojoBuilder filePojoBuilder = FilePojo.builder();
    private final HashMap<String, String> entityClassNameToFullName = new HashMap<>();
    private final HashMap<String, String> revEntityClassNameToFullName = new HashMap<>();
    private final HashMap<String, Set<DiePojo>> entityToDies = new HashMap<>();
    private final List<Map<String, Object>> reflectionData = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException {
        try {
            if (!resourcesDirectory.exists()) {
                resourcesDirectory.mkdirs();
            }
            if (!nativeImageDirectory.exists()) {
                nativeImageDirectory.mkdirs();
            }

            reflectionFile = new File(resourcesDirectory, "reflection.json");
            reflectConfigFile = new File(nativeImageDirectory, "reflect-config.json");

            if (!reflectionFile.exists()) reflectionFile.createNewFile();
            if (!reflectConfigFile.exists()) reflectConfigFile.createNewFile();

            SourceRoot sourceRoot = new SourceRoot(Paths.get(sourceDirectory.toURI()));
            classHierarchy = new HashMap<>();

            List<ParseResult<CompilationUnit>> compilationUnits = sourceRoot.tryToParse();

            for (ParseResult<CompilationUnit> parseResult : compilationUnits) {
                Optional<CompilationUnit> result = parseResult.getResult();
                result.ifPresent(compilationUnit -> compilationUnit.accept(new ClassVisitor(), classHierarchy));
            }

            for (ParseResult<CompilationUnit> parseResult : compilationUnits) {
                Optional<CompilationUnit> result = parseResult.getResult();
                result.ifPresent(compilationUnit -> {
                    compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                        EntityPojo entityPojo = processClass(clazz);
                        if (entityPojo != null) {
                            filePojoBuilder.entity(entityPojo);
                        }
                    });
                });
            }

            FilePojo filePojo = filePojoBuilder.build();
            filePojo.getEntities().forEach(entity -> {
                entity.getFields().forEach(field -> {
                    List<String> generics = field.getGeneric();
                    if (generics != null) {
                        List<String> updatedGenerics = generics.stream()
                                .map(entityClassNameToFullName::get)
                                .toList();
                        generics.clear();
                        generics.addAll(updatedGenerics);
                    }
                });
                entity.setDies(entityToDies.get(revEntityClassNameToFullName.get(entity.getName())));
            });

            filePojo.write(reflectionFile);
//            writeReflectionFile(reflectConfigFile);
            getLog().info("Reflection metadata generated: " + reflectConfigFile.getAbsolutePath());

        } catch (IOException e) {
            throw new MojoExecutionException("Error parsing source files", e);
        }
    }

    private EntityPojo processClass(ClassOrInterfaceDeclaration classDeclaration) {
        getLog().info("Found class: " + classDeclaration.getNameAsString());
        if (isAncestor(classDeclaration.getNameAsString(), ENTITY_SUPERCLASS, classHierarchy)) {
            getLog().info("Found entity: " + classDeclaration.getFullyQualifiedName().orElse(""));

            String fullClassName = classDeclaration.getFullyQualifiedName().orElse(classDeclaration.getNameAsString());
            entityClassNameToFullName.put(classDeclaration.getNameAsString(), fullClassName);
            revEntityClassNameToFullName.put(fullClassName, classDeclaration.getNameAsString());

            EntityPojo.EntityPojoBuilder builder = EntityPojo.builder().name(fullClassName);

            classDeclaration.findAll(FieldDeclaration.class).forEach(field -> {
                boolean dieTogether = field.isAnnotationPresent("DieTogether");
                for (VariableDeclarator variable : field.getVariables()) {
                    if (variable.getType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType classOrInterfaceType = variable.getType().asClassOrInterfaceType();
                        NodeList<Type> types = classOrInterfaceType.getTypeArguments().orElseGet(() -> null);
                        String nameAsString = variable.getNameAsString();
                        FieldPojo pojo = FieldPojo.
                                builder().
                                name(nameAsString).
                                type(classOrInterfaceType.getNameAsString()).
                                generic(types == null ? null : types.stream().map(Type::asString).collect(Collectors.<String>toList())).
                                build();
                        builder.field(
                                pojo
                        );
                        if (dieTogether && types != null) {
                            String type = types.stream().map(Type::asString).findFirst().orElse(null);
                            if (type != null)
                                entityToDies.computeIfAbsent(type, k -> new HashSet<>()).add(
                                        DiePojo.builder().
                                                className(fullClassName).
                                                fieldName(nameAsString).
                                                build()
                                );
                            System.out.println("entityToDies = " + entityToDies);
                        }
                    }
                }
            });

            Map<String, Object> classMetadata = new HashMap<>();
            classMetadata.put("name", fullClassName);
            classMetadata.put("allDeclaredFields", true);
            classMetadata.put("allDeclaredMethods", true);
            classMetadata.put("allDeclaredConstructors", true);
            classMetadata.put("annotations", classDeclaration.getAnnotations().stream()
                    .map(annotationExpr -> "\"" + annotationExpr.getNameAsString() + "\"")
                    .collect(Collectors.toList()));

            reflectionData.add(classMetadata);

            return builder.build();
        }
        return null;
    }

    private static class ClassVisitor extends VoidVisitorAdapter<Map<String, Set<String>>> {
        @Override
        public void visit(ClassOrInterfaceDeclaration cid, Map<String, Set<String>> classHierarchy) {
            super.visit(cid, classHierarchy);
            String className = cid.getNameAsString();
            Set<String> supers = classHierarchy.computeIfAbsent(className, k -> new HashSet<>());
            cid.getExtendedTypes().forEach(extendedType -> supers.add(extendedType.getNameAsString()));
            cid.getImplementedTypes().forEach(implementedType -> supers.add(implementedType.getNameAsString()));
        }
    }

    private boolean isAncestor(String className, String potentialAncestor, Map<String, Set<String>> classHierarchy) {
        if (classHierarchy.containsKey(className)) {
            Set<String> superClasses = classHierarchy.get(className);
            if (superClasses.contains(potentialAncestor)) {
                return true;
            }
            for (String superClass : superClasses) {
                if (isAncestor(superClass, potentialAncestor, classHierarchy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void writeReflectionFile(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[\n");
            for (int i = 0; i < reflectionData.size(); i++) {
                writer.write("  " + toJson(reflectionData.get(i)));
                if (i < reflectionData.size() - 1) writer.write(",");
                writer.write("\n");
            }
            writer.write("]");
        }
    }

    private String toJson(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(entry -> "    \"" + entry.getKey() + "\": " + (entry.getValue() instanceof String
                        ? "\"" + entry.getValue() + "\""
                        : entry.getValue()))
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }
}
