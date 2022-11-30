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
package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.names.NumberKind;
import com.mastfrog.smithy.simple.extensions.UnitsTrait;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 *
 * @author Tim Boudreau
 */
final class ConversionMethodGenerator implements StructureContributor {

    static final ConversionMethodGenerator INSTANCE = new ConversionMethodGenerator();

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        if (!cb.containsMethodNamed("to")) {
            withConversionParams(helper, (enumMember, numberMember) -> {
                generateUnitConversionMethod(cb, enumMember, helper, numberMember);
                generateParseMethod(cb, enumMember, helper, numberMember);
                generateFormatMethod(cb, helper, numberMember);
                return null;
            });
        }
    }

    private <T> void generateFormatMethod(ClassBuilder<T> cb, StructureGenerationHelper helper, StructureMember<NumberShape> numberMember) {
        cb.method("formatValue", mth -> {
            cb.importing(NumberFormat.class);
            mth.docComment("Format the value of this " + cb.className()
                    + " using the passed formatter."
                    + "\n@param formatter a NumberFormat"
                    + "\n@return a string")
                    .withModifier(PUBLIC)
                    .addArgument("NumberFormat", "formatter")
                    .returning("String")
                    .body(bb -> {
                        helper.generateNullCheck("formatter", bb, cb);
                        bb.returningInvocationOf("format")
                                .withArgument(numberMember.field())
                                .on("formatter");
                    });
        });
    }

    private <T> void generateParseMethod(ClassBuilder<T> cb, StructureMember<EnumShape> enumMember, StructureGenerationHelper helper, StructureMember<NumberShape> numberMember) {
        cb.method("parse", mth -> {
            cb.importing(Optional.class);
            Random rnd = new Random(enumMember.target().getId().hashCode());
            int rn = rnd.nextInt(enumMember.target().getAllMembers().size());
            String randomMemberName = new ArrayList<>(enumMember.target().getAllMembers().keySet()).get(rn)
                    .toLowerCase().replace('_', '-');

            boolean isFloat = numberMember.target().getType() == ShapeType.BIG_DECIMAL
                    || numberMember.target().getType() == ShapeType.FLOAT
                    || numberMember.target().getType() == ShapeType.DOUBLE;

            String sampleValue;
            if (isFloat) {
                sampleValue = Double.toString(rnd.nextDouble() * rnd.nextInt(100)) + " " + randomMemberName;
            } else {
                sampleValue = Integer.toString(rnd.nextInt(100)) + " " + randomMemberName;
            }

            mth.withModifier(PUBLIC, STATIC)
                    .docComment("Parses a space separated " + numberMember.typeName() + " and " + enumMember.typeName()
                            + " using the fuzzy-match mechanism which can handle case- "
                            + "and underscore-vs-hyphen differences - for example, parsing "
                            + "<code>" + sampleValue + "</code> into an appropriate "
                            + "instance of " + cb.className() + "."
                            + "\n@param input a string"
                            + "\n@return an optional which returns an instance of "
                            + cb.className() + " if parsing succeeds")
                    .addArgument("String", "input")
                    .returning("Optional<" + cb.className() + ">")
                    .body(bb -> {
                        bb.ifNull("input").returningInvocationOf("empty").on("Optional").endIf();
                        bb.declare("parts")
                                .initializedByInvoking("split")
                                .withStringLiteral("\\s+")
                                .onInvocationOf("trim")
                                .on("input")
                                .as("String[]");
                        bb.iff(variable("parts.length").isNotEqualTo(number(2)))
                                .returningInvocationOf("empty").on("Optional")
                                .endIf();
                        bb.declare("units")
                                .initializedByInvoking("find")
                                .withArgument("parts[1]")
                                .on(enumMember.typeName())
                                .as("Optional<" + enumMember.typeName() + ">");

                        bb.iff().booleanExpression("!units.isPresent()")
                                .returningInvocationOf("empty").on("Optional")
                                .endIf();

                        bb.trying(tri -> {
                            NumberKind kind = NumberKind.forShape(numberMember.target());
                            tri.declare("value")
                                    .initializedByInvoking(kind.parseMethod())
                                    .withArgument("parts[0]")
                                    .on(kind.boxedType())
                                    .as(kind.primitiveTypeName());
                            int enumPos = helper.members().indexOf(enumMember);
                            int numberPos = helper.members().indexOf(numberMember);
                            assert enumPos != numberPos;
                            if (enumPos < numberPos) {
                                tri.returningInvocationOf("map")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("unitValue")
                                                    .body(lbb -> {
                                                        lbb.returningNew(nb -> {
                                                            nb.withArgument("unitValue")
                                                                    .withArgument("value")
                                                                    .ofType(cb.className());
                                                        });
                                                    });
                                        })
                                        .on("units");
                            } else {
                                tri.returningInvocationOf("map")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("unitValue")
                                                    .body(lbb -> {
                                                        lbb.returningNew(nb -> {
                                                            nb.withArgument("value")
                                                                    .withArgument("unitValue")
                                                                    .ofType(cb.className());
                                                        });
                                                    });
                                        })
                                        .on("units");
                            }
                            tri.catching("NumberFormatException").endTryCatch();
                        });

                        bb.returningInvocationOf("empty").on("Optional");
                    });
        });
    }

    private <T> void generateUnitConversionMethod(ClassBuilder<T> cb, StructureMember<EnumShape> enumMember, StructureGenerationHelper helper, StructureMember<NumberShape> numberMember) {
        cb.method("to", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Convert this " + cb.className() + " to an instance using "
                            + " the conversion methods defined by the @unit trait on " + enumMember.typeName()
                            + "."
                            + "\n@param to the unit to convert to"
                            + "\n@return a new instance of " + cb.className()
                            + " or this if the passed " + enumMember.field() + " is the same as this one's."
                    )
                    .addArgument(enumMember.typeName(), "to")
                    .returning(cb.className())
                    .body(bb -> {
                        helper.generateNullCheck("to", bb, cb);
                        bb.iff(variable("to").isEqualTo(variable("this." + enumMember.field())))
                                .returningThis().endIf();
                        bb.declare("converted")
                                .initializedByInvoking("convert")
                                .withArgument(numberMember.field())
                                .withArgument(enumMember.field())
                                .on("to")
                                .as(numberMember.typeName());
                        int enumPos = helper.members().indexOf(enumMember);
                        int numberPos = helper.members().indexOf(numberMember);
                        assert enumPos != numberPos;
                        if (enumPos < numberPos) {
                            bb.returningNew(nb -> {
                                nb.withArgument("to")
                                        .withArgument("converted")
                                        .ofType(cb.className());
                            });
                        } else {
                            bb.returningNew(nb -> {
                                nb.withArgument("converted")
                                        .withArgument("to")
                                        .ofType(cb.className());
                            });
                        }
                    });
        });
    }

    private static <T> T withConversionParams(StructureGenerationHelper helper,
            BiFunction<StructureMember<EnumShape>, StructureMember<NumberShape>, T> c) {
        List<StructureMember<?>> mems = helper.members();
        if (mems.size() != 2) {
            return null;
        }
        StructureMember<EnumShape> enumMember = null;
        StructureMember<NumberShape> numberMember = null;
        for (StructureMember<?> mem : mems) {
            if (mem.target().getType() == ShapeType.ENUM) {
                enumMember = mem.as(EnumShape.class).get();
            } else {
                switch (mem.target().getType()) {
                    case INTEGER:
                    case LONG:
                    case DOUBLE:
                    case FLOAT:
                        numberMember = mem.as(NumberShape.class).get();
                }
            }
        }
        if (enumMember == null || numberMember == null) {
            return null;
        }
        for (Map.Entry<String, MemberShape> e : enumMember.target().getAllMembers().entrySet()) {
            if (!e.getValue().getTrait(UnitsTrait.class).isPresent()) {
                return null;
            }
        }
        return c.apply(enumMember, numberMember);
    }

    public static boolean canGenerateConversionMethods(StructureGenerationHelper helper) {
        Boolean result = withConversionParams(helper, (enumMember, numberMember) -> {
            return true;
        });
        return result == null ? false : result;
    }

}
