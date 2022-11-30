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
package com.mastfrog.smithy.java.generators.base;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.Problems;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.telenav.validation.ValidationExceptionProvider;
import com.mastfrog.smithy.java.generators.builtin.struct.Namer;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import static com.telenav.smithy.names.JavaTypes.packageOf;
import com.telenav.smithy.utils.ShapeUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Collections.unmodifiableList;
import java.util.Iterator;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractStructureGenerator extends AbstractJavaGenerator<StructureShape> {

    protected final StructureGenerationHelper helper;

    public AbstractStructureGenerator(StructureShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        this.helper = new HelperImpl();
    }

    protected boolean isOmitted(MemberShape member) {
        return false;
    }

    @Override
    public void prepare(GenerationTarget target, Model model, SmithyGenerationContext ctx, Problems problems) {
        ((HelperImpl) helper).pruneOmittedMembers();
    }

    final class HelperImpl implements StructureGenerationHelper {

        private final List<StructureMember<?>> members = new ArrayList<>();
        private final Namer namer = Namer.getDefault();

        HelperImpl() {
            shape.members().forEach(mem -> {
                Shape target = model.expectShape(mem.getTarget());
                members.add(StructureMember.create(mem, target, this));
            });
        }

        void pruneOmittedMembers() {
            for (Iterator<StructureMember<?>> it = members.iterator(); it.hasNext();) {
                StructureMember<?> sm = it.next();
                if (isOmitted(sm.member())) {
                    it.remove();
                }
            }
        }

        public Namer namer() {
            return namer;
        }

        @Override
        public boolean isOmitted(MemberShape shape) {
            return AbstractStructureGenerator.this.isOmitted(shape);
        }

        @Override
        public <T> void generateNullCheck(String variable, ClassBuilder.BlockBuilderBase<?, ?, ?> bb, ClassBuilder<T> on) {
            ValidationExceptionProvider.generateNullCheck(variable, bb, on);
        }

        @Override
        public <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void generateEqualityCheckOfNullable(String v, String compareWith, B bldr) {
            AbstractStructureGenerator.this.generateEqualityCheckOfNullable(v, compareWith, bldr);
        }

        @Override
        public Model model() {
            return AbstractStructureGenerator.this.model;
        }

        @Override
        public StructureShape structure() {
            return shape;
        }

        @Override
        public SmithyGenerationContext context() {
            return AbstractStructureGenerator.this.ctx();
        }

        @Override
        public List<StructureMember<?>> members() {
            return unmodifiableList(members);
        }

        @Override
        public ValidationExceptionProvider validation() {
            return ValidationExceptionProvider.validationExceptions();
        }

        @Override
        public <T, R> String generateInitialEqualsTest(ClassBuilder<R> cb, ClassBuilder.BlockBuilder<T> bb) {
            return AbstractStructureGenerator.this.generateInitialEqualsTest(cb, bb);
        }

        @Override
        public void maybeImport(ClassBuilder<?> cb, String... fqns) {
            List<String> fq = new ArrayList<>(Arrays.asList(fqns));
            // Prune imports from the same package
            for (Iterator<String> it = fq.iterator(); it.hasNext();) {
                String s = it.next();
                String pk = packageOf(s);
                if (shape.getId().getNamespace().equals(pk)) {
                    it.remove();
                }
            }
            if (fq.isEmpty()) {
                return;
            }
            String[] fqns1 = fq.toArray(String[]::new);
            ShapeUtils.maybeImport(cb, fqns1);
        }
    }

}
