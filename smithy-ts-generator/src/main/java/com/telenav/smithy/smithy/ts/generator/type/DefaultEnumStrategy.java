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
package com.telenav.smithy.smithy.ts.generator.type;

import com.telenav.smithy.ts.vogon.TypescriptSource;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 *
 * @author Tim Boudreau
 */
class DefaultEnumStrategy extends AbstractEnumStrategy {

    public DefaultEnumStrategy(EnumShape shape, TypeStrategies strategies) {
        super(shape, strategies);
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void instantiateFromRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, A extends TypescriptSource.InvocationBuilder<B>, B extends TypescriptSource.Invocation<T, B, A>> void instantiateFromRawJsonObject(B block, TsVariable rawVar) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T, B extends TypescriptSource.TsBlockBuilderBase<T, B>> void convertToRawJsonObject(B block, TsVariable rawVar, String instantiatedVar, boolean declare) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public TsSimpleType rawVarType() {
        return TsPrimitiveTypes.STRING;
    }

    @Override
    public TsSimpleType shapeType() {
        return new TsShapeType(shape, strategies.types(), false, false);
    }

    @Override
    public <T> T applyDefault(DefaultTrait def, TypescriptSource.ExpressionBuilder<T> ex) {
        return ex.element().literal(defaultValue(def)).of(targetType());
    }

}
