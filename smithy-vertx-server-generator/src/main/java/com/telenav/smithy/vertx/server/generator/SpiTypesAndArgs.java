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
package com.telenav.smithy.vertx.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 *
 * @author Tim Boudreau
 */
class SpiTypesAndArgs {

    private final List<String> types = new ArrayList<>(5);
    final List<String> args = new ArrayList<>(5);
    private final Set<Integer> injectIntoOutputWriter = new HashSet<>(3);
    static final Set<String> additionalImports = new HashSet<>();

    public void add(String typeFqn, String varName) {
        add(typeFqn, varName, false);
    }

    private void checkIsFqn(String typeFqn) {
        if (typeFqn.indexOf('.') < 0) {
            throw new IllegalArgumentException("Not a qualified type name: " + typeFqn);
        }
    }

    public void alsoImport(String fqn) {
        additionalImports.add(fqn);
    }

    public void add(String typeFqn, String varName, boolean inject) {
        checkIsFqn(typeFqn);
        if (inject) {
            injectIntoOutputWriter.add(types.size());
        }
        types.add(typeFqn);
        args.add(varName);
    }

    void eachInjectableType(BiConsumer<String, String> c) {
        for (Integer i : injectIntoOutputWriter) {
            c.accept(types.get(i), args.get(i));
        }
    }

    public void importTypes(ClassBuilder<String> cb) {
        for (String t : types) {
            int gix = t.indexOf('<');
            if (gix > 0) {
                t = t.substring(0, gix);
            }
            cb.importing(t);
        }
        additionalImports.forEach(cb::importing);
    }

}
