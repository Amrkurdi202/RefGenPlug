package com.quentity.refGenPlug;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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

  private static final String ENTITY_SUPERCLASS = "Entity";
  private static final String RES_SUPERCLASS = "Res";
  private Map<String, Set<String>> classHierarchy;
  private File reflectionFile;
  private FilePojo.FilePojoBuilder filePojoBuilder = FilePojo.builder();
  private final HashMap<String, String> entityClassNameToFullName = new HashMap<>();

  @Override
  public void execute() throws MojoExecutionException {
    try {
      if (!resourcesDirectory.exists()) {
        resourcesDirectory.mkdirs();
      }
      reflectionFile = Arrays.stream(resourcesDirectory.listFiles((dir, name) -> name.endsWith(".json"))).findFirst().orElseGet(() -> new File(resourcesDirectory + "/reflection.json"));
      if (!reflectionFile.exists())
        reflectionFile.createNewFile();
      if (reflectionFile == null) {
        getLog().info("No reflection files found or created");
        return;
      }
      SourceRoot sourceRoot = new SourceRoot(Paths.get(sourceDirectory.toURI()));
      classHierarchy = new HashMap<>();

      List<ParseResult<CompilationUnit>> compilationUnits = sourceRoot.tryToParse();

      for (ParseResult<CompilationUnit> parseResult : compilationUnits) {
        Optional<CompilationUnit> result = parseResult.getResult();
        result.ifPresent(compilationUnit -> {
          compilationUnit.accept(new ClassVisitor(), classHierarchy);
        });
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
      filePojo.getEntities()
              .forEach(entity -> entity.getFields()
                      .forEach(field -> {
                        // Get generics safely, default to an empty list if null
                        List<String> generics = field.getGeneric();
                        if (generics != null) {
                          // Replace each string in `genaric` with its mapped value
                          List<String> updatedGenerics = generics.stream()
                                  .map(entityClassNameToFullName::get)
                                  .toList();
                          // Set the updated list back to the field
                          generics.clear();
                          generics.addAll(updatedGenerics);
                        }
                      }));


      filePojo.write(reflectionFile);

    } catch (IOException e) {
      throw new MojoExecutionException("Error parsing source files", e);
    }
  }

  private EntityPojo processClass(ClassOrInterfaceDeclaration classDeclaration) {
    getLog().info("Found class: " + classDeclaration.getNameAsString());
    if (isAncestor(classDeclaration.getNameAsString(), ENTITY_SUPERCLASS, classHierarchy)) {
      getLog().info("Found entity: " + classDeclaration.getFullyQualifiedName().orElse(""));
      String className = classDeclaration.getNameAsString();
      String fullClassName = classDeclaration.getFullyQualifiedName().orElse(className);
      entityClassNameToFullName.put(className, fullClassName);
      EntityPojo.EntityPojoBuilder builder = EntityPojo.builder().name(fullClassName);

      classDeclaration.findAll(FieldDeclaration.class).forEach(field -> {
        for (VariableDeclarator variable : field.getVariables()) {
          if (variable.getType().isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrInterfaceType = variable.getType().asClassOrInterfaceType();
            NodeList<Type> types = classOrInterfaceType.getTypeArguments().orElseGet(() -> null);
            builder.field(
                    FieldPojo.
                            builder().
                            name(variable.getNameAsString()).
                            type(classOrInterfaceType.getNameAsString()).
                            generic(types == null ? null : types.stream().map(Type::asString).collect(Collectors.<String>toList())).
                            build()
            );
          }
        }
      });
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
      cid.getExtendedTypes().forEach(extendedType -> {
        String superClassName = extendedType.getNameAsString();
        supers.add(superClassName);
      });
      cid.getImplementedTypes().forEach(implementedType -> {
        String interfaceName = implementedType.getNameAsString();
        supers.add(interfaceName);
      });
    }
  }

  private boolean isAncestor(String className, String potentialAncestor, Map<String, Set<String>> classHierarchy) {
    if (classHierarchy.containsKey(className)) {
      Set<String> superClasses = classHierarchy.get(className);
      for (String superClass : superClasses) {
        if (superClass.equals(potentialAncestor)) {
          return true;
        }
      }
      for (String superClass : superClasses) {
        return isAncestor(superClass, potentialAncestor, classHierarchy);
      }
    }
    return false;
  }
}