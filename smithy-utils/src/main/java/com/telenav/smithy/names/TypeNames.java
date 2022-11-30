/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.names;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.strings.Strings;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 * Generates type names for elements.
 *
 * @author Tim Boudreau
 */
public final class TypeNames {

    private final Model model;

    public TypeNames(Model model) {
        this.model = model;
    }
    
    public Model model() {
        return model;
    }

    public String packageOf(Shape shape) {
        String result = packageOf(shape.getId());
        if (result.startsWith("java")
                || result.startsWith("software") || result.startsWith("smithy")) {
            return result;
        }
        Optional<Class<?>> cl = JavaTypes.type(shape.toShapeId().toString(), true);
        if (cl.isPresent()) {
            if (cl.get().isPrimitive()) {
                return "";
            } else if (cl.get() == List.class) {
                return "";
            }
            return cl.get().getPackageName();
        }
        switch (shape.getType()) {
            case LIST:
            case INTEGER:
            case STRING:
            case SHORT:
            case LONG:
            case DOUBLE:
            case BYTE:
            case BIG_DECIMAL:
            case BIG_INTEGER:
            case FLOAT:
            case TIMESTAMP:
            case BOOLEAN:
            case MAP:
            case INT_ENUM:
            case ENUM:
            case MEMBER:
            case STRUCTURE:
            case DOCUMENT:
            case BLOB:
            case UNION:
                return result + ".model";
        }
        if (shape.isOperationShape()) {
            return result + ".spi";
        } else if (shape.isResourceShape()) {
            return result + ".http";
        }
        return result;
    }

    public static String packageOf(String what) {
        int ix = what.lastIndexOf('.');
        if (ix > 0) {
            return what.substring(0, ix);
        }
        return "";
    }

    public static String packageOf(ShapeId shape) {
        String result = JavaTypes.type(shape.toString(), true)
                .map(type -> type.getPackageName())
                .orElse(shape.getNamespace());
        return result;
    }

    public String qualifiedNameOf(Shape shape, ClassBuilder<?> on, boolean required) {
        String nm = typeNameOf(on, shape, required);
        String pkg = packageOf(shape);
        if ("smithy.api".equals(pkg)) {
            return nm;
        }
        return pkg + '.' + nm;
    }

    public static String typeNameOf(Shape shape) {

        return JavaTypes.type(shape.getId().toString(), true)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(shape.getId().getName()));
    }

    public static String typeNameOf(Shape shape, boolean required) {
        return JavaTypes.type(shape.getId().toString(), required)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(shape.getId().getName()));
    }

    public static String typeNameOf(ShapeId id) {
        return JavaTypes.type(id.toString(), true)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(id.getName()));
    }

    public static String typeNameOf(ShapeId id, boolean required) {
        return JavaTypes.type(id.toString(), required)
                .map(type -> type.getSimpleName())
                .orElse(JavaSymbolProvider.escape(id.getName()));
    }

    @SuppressWarnings("deprecation")
    public String typeNameOf(ClassBuilder<?> on, Shape shape, boolean required) {
        if (shape.isListShape() || shape instanceof ListShape) {
            ListShape ls = (ListShape) shape;
            boolean isSet = ls.getTrait(UniqueItemsTrait.class).isPresent();
            Shape realTarget = model.expectShape(ls.getMember().getTarget());
            if (isSet) {
                on.importing(Set.class);
                String tn = typeNameOf(on, realTarget, false);
                return "Set<" + tn + ">";
            } else {
                on.importing(List.class);
                String tn = typeNameOf(on, realTarget, false);
                return "List<" + tn + ">";
            }
        } else if (shape instanceof software.amazon.smithy.model.shapes.SetShape) {
            on.importing(Set.class);
            software.amazon.smithy.model.shapes.SetShape ss = (software.amazon.smithy.model.shapes.SetShape) shape;
            Shape realTarget = model.expectShape(ss.getMember().getTarget());
            String tn = typeNameOf(on, realTarget, true);
            return "Set<" + tn + ">";
        }
        String result = typeNameOf(shape.getId(), required);
        String resultPkg = packageOf(result);
        String ns = shape.getId().getNamespace();
        if (!resultPkg.isEmpty() && !resultPkg.equals(ns) && !"java.lang".equals(ns)) {
            on.importing(resultPkg + "." + result);
        }
        return result;
    }

    public static String simpleNameOf(String typeName) {
        int ix = typeName.lastIndexOf('.');
        return ix < 0 ? typeName : typeName.substring(ix + 1);
    }

    public static String enumConstantName(String s) {
        return escape(Strings.camelCaseToDelimited(s, '_').toUpperCase());
    }
    
}
