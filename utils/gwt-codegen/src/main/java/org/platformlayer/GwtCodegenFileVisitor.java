package org.platformlayer;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.platformlayer.model.ClassModel;
import org.platformlayer.model.FieldModel;

import com.google.common.base.Joiner;

public class GwtCodegenFileVisitor extends FileVisitor {
    private final ClassInspection classInspection;
    private final TemplateEngine template;
    private List<String> gwtBasePathComponents;

    public GwtCodegenFileVisitor(File srcDir, File outDir, Log log, ClassInspection classInspection, TemplateEngine template) {
        super(srcDir, outDir, log);
        this.classInspection = classInspection;
        this.template = template;
    }

    @Override
    public void visitDirectory(File dir) throws MojoExecutionException {
        for (File gwtFile : dir.listFiles(new ExtensionFileFilter(".gwt.xml"))) {
            gwtBasePathComponents = getPathComponents();
        }

        super.visitDirectory(dir);
    }

    @Override
    public void visitFile(File file) throws MojoExecutionException {
        String fileName = file.getName();
        if (!fileName.endsWith(".class")) {
            return;
        }

        String simpleClassName = fileName.substring(0, fileName.length() - 6);
        String fullClassName = Joiner.on(".").join(getPathComponents()) + "." + simpleClassName;

        try {
            log.info("Processing " + fullClassName);
            Class<?> clazz = classInspection.loadClass(fullClassName);

            if (classInspection.findAnnotation(clazz, "org.platformlayer.codegen.GwtModel") == null) {
                log.info("No GWTModel annotation; skipping");
                return;
            }

            processClass(clazz);
            // getLog().info(" => " + converted);
        } catch (ClassNotFoundException e) {
            log.warn("Error loading class: " + fullClassName, e);
        } catch (NoClassDefFoundError e) {
            log.warn("Error loading class: " + fullClassName, e);
        }
    }

    private void runTemplate(String templateName, Map<String, Object> model, File outputFile) throws MojoExecutionException {
        Utils.mkdirs(outputFile.getParentFile());
        String result = template.runTemplateToString(getClass().getPackage().getName().replace(".", "/") + "/" + templateName, model);
        Utils.writeAll(outputFile, result);
    }

    private void processClass(Class<?> clazz) throws MojoExecutionException {
        Map<String, Object> model = new HashMap<String, Object>();

        ClassModel classModel = new ClassModel();
        classModel.className = clazz.getSimpleName();
        classModel.proxyClassName = classModel.className + "Proxy";
        classModel.serviceClassName = classModel.className + "GwtService";
        classModel.editorClassName = classModel.className + "Editor";

        for (Field field : clazz.getFields()) {
            FieldModel fieldModel = new FieldModel();
            Class<?> type = field.getType();

            if (!isNativeType(type)) {
                continue;
            }

            Class<?> accessorType = type;
            if (accessorType.isPrimitive()) {
                accessorType = Utils.getBoxedType(accessorType);
            }
            String fieldName = field.getName();
            String beanName = Utils.capitalize(fieldName);

            fieldModel.type = type.getName();
            fieldModel.accessorType = accessorType.getName();
            fieldModel.beanName = beanName;
            fieldModel.name = fieldName;

            classModel.fields.add(fieldModel);
        }

        model.put("className", classModel.className);
        model.put("proxyClassName", classModel.proxyClassName);
        model.put("serviceClassName", classModel.serviceClassName);
        model.put("editorClassName", classModel.editorClassName);
        model.put("fields", classModel.fields);

        if (gwtBasePathComponents == null) {
            throw new MojoExecutionException("Did not find .gwt.xml file above " + clazz);
        }

        File gwtOutDir = new File(outDir, Joiner.on("/").join(gwtBasePathComponents));
        Utils.mkdirs(gwtOutDir);

        String gwtPackage = Joiner.on(".").join(gwtBasePathComponents);
        model.put("gwtPackage", gwtPackage);

        String modelPackage = Joiner.on(".").join(getPathComponents());
        model.put("modelPackage", modelPackage);

        String editorPackage = gwtPackage + ".client";
        model.put("editorPackage", editorPackage);

        runTemplate("GwtProxy.ftl", model, new File(new File(gwtOutDir, "shared"), classModel.proxyClassName + ".java"));
        runTemplate("GwtRequestFactory.ftl", model, new File(new File(gwtOutDir, "shared"), classModel.className + "RequestFactory.java"));
        runTemplate("GwtService.ftl", model, new File(new File(gwtOutDir, "server"), classModel.serviceClassName + ".java"));
    }

    private boolean isNativeType(Class<?> type) {
        if (type.isPrimitive())
            return true;
        if (type == String.class)
            return true;

        if (type == Boolean.class)
            return true;
        if (type == Byte.class)
            return true;
        if (type == Character.class)
            return true;
        if (type == Short.class)
            return true;
        if (type == Integer.class)
            return true;
        if (type == Long.class)
            return true;
        if (type == Float.class)
            return true;
        if (type == Double.class)
            return true;

        return false;
    }

    // private void convertClasses(String baseNamespace, String relativePath, File srcDir, File outputDir) throws MojoExecutionException {
    // if (!srcDir.exists())
    // return;
    //
    // for (File gwtFile : srcDir.listFiles(new FileFilter() {
    // };() {
    //
    // public boolean accept(File arg0, String arg1) {
    // // TODO Auto-generated method stub
    // return false;
    // }
    // })
    // for (File file : srcDir.listFiles()) {
    // String fileName = file.getName();
    // if (file.isDirectory()) {
    // String childPath = relativePath;
    // childPath += fileName + ".";
    // convertClasses(baseNamespace, childPath, file, new File(outputDir, fileName));
    // } else {
    // if (!fileName.endsWith(".class")) {
    // continue;
    // }
    // String className = relativePath;
    // String simpleClassName = fileName.substring(0, fileName.length() - 6);
    // className += simpleClassName;
    //
    // try {
    // getLog().info("Processing " + className);
    // Class<?> clazz = classLoader.loadClass(className);
    // if (clazz.isInterface()) {
    // getLog().info("Ignoring interface");
    // continue;
    // }
    //
    // if (findAnnotation(clazz, "org.platformlayer.codegen.GwtModel") == null) {
    // getLog().info("No GWTModel annotation; skipping");
    // continue;
    // }
    //
    // processClass(baseNamespace, clazz, outputDir);
    // // getLog().info(" => " + converted);
    // } catch (ClassNotFoundException e) {
    // throw new MojoExecutionException("Error loading class: " + className, e);
    // } catch (NoClassDefFoundError e) {
    // throw new MojoExecutionException("Error loading class: " + className, e);
    // }
    // }
    // }
    //
    // }
    //
    // }

}
