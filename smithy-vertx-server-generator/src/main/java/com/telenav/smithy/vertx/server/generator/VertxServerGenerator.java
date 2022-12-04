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

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Obj;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ElseClauseBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TryBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import com.mastfrog.java.vogon.ClassBuilder.Variable;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.mastfrog.smithy.server.common.Declaration;
import com.mastfrog.smithy.server.common.DeclarationClose;
import com.mastfrog.smithy.server.common.Declarer;
import com.mastfrog.smithy.server.common.Input;
import com.mastfrog.smithy.server.common.InputMemberObtentionStrategy;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBigDecimal;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBigInteger;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBoolean;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToIntEnum;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToNewFromString;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToTimestamp;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.originMethod;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.originMethodCastTo;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.splitToMappedCollection;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.splitToStringSet;
import com.mastfrog.smithy.server.common.OriginType;
import static com.mastfrog.smithy.server.common.OriginType.HTTP_HEADER;
import static com.mastfrog.smithy.server.common.OriginType.URI_PATH;
import static com.mastfrog.smithy.server.common.OriginType.URI_QUERY;
import com.mastfrog.smithy.server.common.PayloadOrigin;
import com.mastfrog.smithy.server.common.RequestParameterOrigin;
import com.mastfrog.smithy.simple.extensions.AuthenticatedTrait;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.names.TypeNames.rawTypeName;
import static com.telenav.smithy.names.TypeNames.simpleNameOf;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.telenav.smithy.names.operation.OperationNames;
import static com.telenav.smithy.names.operation.OperationNames.authPackage;
import static com.telenav.smithy.names.operation.OperationNames.authenticateWithInterfaceName;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceFqn;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceName;
import static com.telenav.smithy.names.operation.OperationNames.serviceAuthenticatedOperationsEnumName;
import com.telenav.smithy.utils.ResourceGraph;
import com.telenav.smithy.utils.ResourceGraphs;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.smithy.utils.ShapeUtils.requiredOrHasDefault;
import com.telenav.smithy.utils.path.PathInfo;
import com.telenav.smithy.utils.path.PathInformationExtractor;
import static com.telenav.validation.ValidationExceptionProvider.validationExceptions;
import static java.beans.Introspector.decapitalize;
import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public class VertxServerGenerator extends AbstractJavaGenerator<ServiceShape> {

    private final ScopeBindings scope = new ScopeBindings();
    private final boolean debug;

    VertxServerGenerator(ServiceShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        super(shape, model, destSourceRoot, target, language);
        debug = settings.is(DEBUG);
    }

    private void initDebug(ClassBuilder<?> cb) {
        applyGeneratedAnnotation(VertxServerGenerator.class, cb);
        if (debug) {
            cb.generateDebugLogCode();
        }
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {

        ResourceGraph graph = ResourceGraphs.graph(model, shape);
        Set<OperationShape> ops = graph.transformedClosure(shape, sh -> sh.isOperationShape() ? sh.asOperationShape().get() : null);

        // Pending - need to sort ops by path to avoid collisions?
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape))
                .named(escape(shape.getId().getName(shape)));
        cb
                .docComment("Guice module to instantiate the " + shape.getId().getName() + " service. "
                        + "A " + cb.className() + " can be used to launch the server, or simply as a Guice module "
                        + "that is part of a larger configuration.  Modules to include on launch can be added via the "
                        + "<code>installing(Module)</code> method, and the vertx and verticle can be customized using "
                        + "<code>UnaryOperator</code>s passed to the appropriate methods."
                        + "\nImplementations of the generated server SPI interfaces can be bound by "
                        + "passing their types to the method for each one.")
                .withModifier(PUBLIC, FINAL)
                .importing(
                        "com.telenav.vertx.guice.VertxGuiceModule",
                        "com.telenav.vertx.guice.verticle.VerticleBuilder",
                        "io.vertx.core.http.HttpMethod",
                        "com.mastfrog.jackson.JacksonModule",
                        "com.google.inject.Binder",
                        "com.google.inject.Module"
                ).implementing("Module");
        initDebug(cb);

        generateStartMethod(cb);

        generateCustomizeVerticleFieldsAndMethods(cb);

        generateSendErrorResponseMethod(cb);
        generateErrorHandlingDisablementMethodAndField(cb);
        generateCreateVertxModuleMethod(cb, graph, ops, addTo);

        generateMainMethod(cb);
        generateConfigureOverride(cb, ops);

        generateCheckTypeMethod(cb);

        cb.sortMembers();
        addTo.accept(cb);
    }

    public void generateConfigureOverride(ClassBuilder<String> cb, Set<OperationShape> ops) {
        String binderVar = "binder";
        cb.overridePublic("configure", mth -> {
            mth.addArgument("Binder", "binder");
            mth.body(bb -> {
                generateOpInterfaceBindingMethodsFieldsAndConfigStanzas(bb, cb, ops);
                generateAuthBindingMethodsAndCalls(bb, cb, ops);
                String moduleVar = "vertxModule";

                bb.declare(moduleVar)
                        .initializedByInvoking("createVertxModule")
                        .inScope()
                        .as("VertxGuiceModule");

                if (!scope.isEmpty()) {
                    scope.generateBindingCode(binderVar, moduleVar, bb, cb);
                } else {
                    bb.lineComment("No scoped bindings needed");
                }

                generateVertxModuleConfigurationStanzas(moduleVar, bb, cb);

                generateModuleAdditionMethod(moduleVar, bb, cb);
                bb.invoke("install").withArgument(moduleVar).on("binder");
                bb.statement("initialized = true");
            });
        });
    }

    public void generateStartMethod(ClassBuilder<String> cb) {
        cb.method("start", mth -> {
            cb.importing("static com.google.inject.Guice.createInjector",
                    "com.telenav.vertx.guice.VertxLauncher",
                    "io.vertx.core.Vertx");

            mth.withModifier(PUBLIC)
                    .docComment("Start the server on the default port."
                            + "\n@return the Vertx instance")
                    .returning("Vertx")
                    .body(bb -> {
                        bb.returningInvocationOf("start")
                                .onInvocationOf("getInstance")
                                .withClassArgument("VertxLauncher")
                                .onInvocationOf("createInjector")
                                .withArgument("this")
                                .inScope();
                    });
        });
        cb.method("start", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Start the server on the a specific port."
                            + "\n@param port the port"
                            + "\n@return the Vertx instance")
                    .addArgument("int", "port")
                    .returning("Vertx")
                    .body(bb -> {
                        bb.iff(variable("port").isLessThanOrEqualTo(number(0)))
                                .andThrow(nb -> nb.withStringLiteral("Port must be > 0").ofType("IllegalArgumentException"))
                                .endIf();
                        bb.returningInvocationOf("start")
                                .onInvocationOf("getInstance")
                                .withClassArgument("VertxLauncher")
                                .onInvocationOf("createInjector")
                                .withArgumentFromInvoking("configuringVerticleWith")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("vb")
                                            .body(lbb -> lbb.invoke("withPort").withArgument("port").on("vb"));
                                })
                                .on("this")
                                .inScope();
                    });

        });
    }

    private <C, B extends BlockBuilderBase<C, B, ?>> String generateModuleAdditionMethod(String moduleVar, B bb, ClassBuilder<String> cb) {
        String modulesField = "modules";
        cb.field("modules")
                .withModifier(PRIVATE, FINAL)
                .initializedWithNew(nb -> nb.ofType("ArrayList<>"))
                .ofType("List<Module>");
        cb.importing(List.class, ArrayList.class);

        cb.method("installing", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Add a module to be installed with this one."
                            + "\n@param module A module"
                            + "\n@return this")
                    .addArgument("Module", "module")
                    .returning(cb.className())
                    .body(addModule -> {
                        addModule.ifNull("module")
                                .andThrow().withStringLiteral("Module may not be null")
                                .ofType("IllegalArgumentException");
                        addModule.invoke("add").withArgument("module").on(modulesField);
                        addModule.returningThis();
                    });
        });
        bb.invoke("forEach")
                .withMethodReference("withModule").on(moduleVar)
                .on(modulesField);

        return modulesField;
    }

    private <C, B extends BlockBuilderBase<C, B, ?>> void generateVertxModuleConfigurationStanzas(String moduleVar, B bb, ClassBuilder<String> cb) {
        cb.importing(Consumer.class);
        String consumerField = "vertxModuleConfigurer";
        String consumerType = "Consumer<VertxGuiceModule>";
        cb.field(consumerField, fld -> {
            fld.withModifier(PRIVATE)
                    .initializedAsLambda(consumerType, lb -> lb.withArgument("ignored").emptyBody());
        });
        cb.method("configuringVertxWith", mth -> {
            mth.withModifier(PUBLIC).addArgument(consumerType, "consumer")
                    .docComment("Customize the vertx instance the server runs in using the "
                            + "passed consumer.  This method may be called multiple times and all passed "
                            + "consumers will be run."
                            + "\n@param consumer A consumer"
                            + "\n@return this")
                    .returning(cb.className())
                    .body(setConsumer -> {
                        setConsumer.invoke("checkInitialized").inScope();
                        setConsumer.ifNull("consumer")
                                .andThrow(nb -> nb.withStringLiteral("Consumer may not be null").ofType("IllegalArgumentException")).endIf();
                        setConsumer.assignField(consumerField)
                                .ofThis()
                                .toInvocation("andThen")
                                .withArgument("consumer")
                                .onField(consumerField).ofThis();
                        setConsumer.returningThis();
                    });
        });
        bb.invoke("accept").withArgument(moduleVar).onField(consumerField).ofThis();
    }

    private <C, B extends BlockBuilderBase<C, B, ?>> void generateOpInterfaceBindingMethodsFieldsAndConfigStanzas(B bb, ClassBuilder<String> cb, Set<OperationShape> ops) {
        cb.field("initialized")
                .withModifier(PRIVATE, VOLATILE)
                .ofType("boolean");
        cb.method("checkInitialized", mth -> {
            mth.withModifier(PRIVATE)
                    .docComment("Used by mutation methods to ensure they cannot be called "
                            + "after the injector has been created, when they would have no effect.")
                    .body(mb -> {
                        mb.iff().booleanExpression("initialized")
                                .andThrow(nb -> nb
                                .withStringLiteral("Cannot configure " + cb.className()
                                        + " after injector creation.  configure() has already been called.")
                                .ofType("IllegalStateException"))
                                .endIf();
                    });
        });
        for (OperationShape sh : ops) {
            cb.importing(operationInterfaceFqn(model, sh));
            String typeName = operationInterfaceName(sh);
            String fieldName = decapitalize(typeName) + "Type";
            String argName = "inbound" + capitalize(fieldName);
            String fieldType = "Class<? extends " + typeName + ">";

            bb.ifNotNull(fieldName).invoke("to")
                    .withArgument(fieldName)
                    .onInvocationOf("bind")
                    .withClassArgument(typeName)
                    .on("binder").endIf();

            cb.field(fieldName, fld -> {
                fld.withModifier(PRIVATE)
                        .ofType(fieldType);
            });

            cb.method("with" + typeName, mth -> {
                mth.withModifier(PUBLIC)
                        .docComment("Provide an implementation of " + typeName + " to run with."
                                + "\n@param " + argName + " a class that implements " + typeName
                                + "\n@return this"
                                + "\n@throws IllegalArgumentException if the value is null, abstract or not actually a subtype of " + typeName
                                + "\n@throws IllegalStateException if configure() has been called and Guice bindings have already been set up"
                        )
                        .addArgument(fieldType, argName)
                        .returning(cb.className())
                        .body(typeAssign -> {
//                            typeAssign.invoke("checkInitialized").inScope();
//                            typeAssign.ifNull(argName)
//                                    .andThrow(nb -> {
//                                        nb.withStringLiteral(argName + " may not be null")
//                                                .ofType("IllegalArgumentException");
//                                    }).endIf();
//                            Value abstractTest = invocationOf("getModifiers")
//                                    .on(argName).and("java.lang.reflect.Modifier.ABSTRACT")
//                                    .parenthesized().isNotEqualTo(number(0)
//                                            .logicalOrWith(invocationOf("isInterface").on(argName)));
//
//                            typeAssign.iff(abstractTest)
//                                    .andThrow(nb -> nb.withStringLiteral("Cannot bind an abstract class or interface")
//                                    .ofType("IllegalArgumentException")).endIf();
//                            typeAssign.iff(invocationOf("isAssignableFrom").withArgument(argName).on(typeName + ".class").isEqualTo("false"))
//                                    .andThrow(nb -> nb.withStringLiteral("Not really a subclass of " + typeName).ofType("IllegalArgumentException")).endIf();
//                            typeAssign.assignField(fieldName)
//                                    .ofThis().toExpression(argName);

                            typeAssign.assignField(fieldName)
                                    .ofThis()
                                    .toInvocation("checkType")
                                    .withArgument(argName)
                                    .withClassArgument(typeName)
                                    .inScope();
                            typeAssign.returningThis();
                        });
                ;
            });
        }
    }

    public void generateCreateVertxModuleMethod(ClassBuilder<String> routerBuilder, ResourceGraph graph, Collection<? extends OperationShape> ops, Consumer<ClassBuilder<String>> addTo) {
        routerBuilder.method("createVertxModule", mth -> {
            mth.withModifier(PRIVATE, FINAL)
                    .returning("VertxGuiceModule")
                    .docComment("Creates a Guice module that sets up a Verticle with all of the "
                            + "operations for the " + shape.getId().getName() + " model."
                            + "\n@return A VertxGuiceModule configured for this service")
                    .body(bb -> {

                        bb.declare("result")
                                .initializedWithNew(nb -> nb.ofType("VertxGuiceModule"))
                                .as("VertxGuiceModule");

                        bb.blankLine().lineComment("Binds ObjectMapper configured to correctly "
                                + " handle").lineComment("java.time types as ISO 8601 strings");
                        bb.invoke("withModule")
                                .withArgumentFromInvoking("loadFromMetaInfServices")
                                .onNew(nb -> nb.ofType("JacksonModule"))
                                .on("result");

                        bb.blankLine().lineComment("Generate a single verticle with routes for")
                                .lineComment("each operation in " + shape.getId().getName());
                        String verticleBuilderName = "verticleBuilder";
                        bb.declare(verticleBuilderName)
                                .initializedByInvoking("withVerticle")
                                .on("result")
                                .as("VerticleBuilder<VertxGuiceModule>");

                        ops.forEach(op -> {
                            List<String> handlers = new ArrayList<>();
                            ClassBuilder<String> operationClass = generateOperation(op, graph, handlers, addTo);
                            bb.blankLine().lineComment("Operation " + op.getId());
                            invokeRoute(bb, verticleBuilderName, routerBuilder, handlers, op);
                            System.out.println("GEN VX OP " + op + " -> " + operationClass.fqn());
                            addTo.accept(operationClass);
                        });

                        bb.invoke("accept")
                                .withArgument(verticleBuilderName)
                                .on("verticleConfigurer");

                        bb.blankLine().lineComment("Translate exceptions we may throw into appropriate responses");

                        generateErrorHandlingRouterCustomizer(bb, routerBuilder, verticleBuilderName);

                        bb.blankLine()
                                .lineComment("Finish the builder, leaving the guice module ready ")
                                .lineComment("to have start() called on it, or to be included with ")
                                .lineComment("other modules in an Injector.");
                        bb.returningInvocationOf("bind").on(verticleBuilderName);
                    });
        });
    }

    public <C, X, B extends BlockBuilderBase<C, B, X>> void generateErrorHandlingRouterCustomizer(B bb, ClassBuilder<String> routerBuilder, String verticleBuilderName) {
        bb.iff().booleanExpression("defaultErrorHandling")
                .invoke("customizingRouterWith")
                .withLambdaArgument(lb -> {
                    lb.withArgument("rtr");
                    lb.body(lbb -> {
                        lbb.lineComment("This code attaches an error handler to the router for the HTTP server")
                                .lineComment("which intercepts what would be 500 responses due to thrown exceptions in")
                                .lineComment("handlers and translates them to appropriate responses");
                        lbb.returningInvocationOf("errorHandler")
                                .withArgument(500)
                                .withLambdaArgument(subLb -> {
                                    subLb.withArgument("ctx");
                                    subLb.body(subBb -> {
                                        subBb.declare("failure")
                                                .initializedByInvoking("failure")
                                                .on("ctx")
                                                .as("Throwable");
                                        Variable v = variable("failure");
                                        routerBuilder.importing(DateTimeParseException.class);
                                        routerBuilder.importing(validationExceptions().fqn());
                                        ClassBuilder.IfBuilder<?> test = subBb.iff(v.isInstance("IllegalArgumentException")
                                                .logicalOrWith(v.isInstance("DateTimeParseException")
                                                        .logicalOrWith(v.isInstance(validationExceptions().name()))));
                                        test.lineComment("These exceptions all indicate input data that could not be parsed.");
                                        test.invoke("sendErrorResponse")
                                                .withArgumentFromInvoking("getMessage")
                                                .on("failure")
                                                .withArgument(400)
                                                .withArgument("ctx")
                                                .inScope();

                                        routerBuilder.importing("com.mastfrog.smithy.http.ResponseException");

                                        test = test.elseIf().variable("failure").instanceOf("ResponseException")
                                                .lineComment("Convenience exception in smithy-java-http-extensions which")
                                                .lineComment("can be used to specify an error response code and message.")
                                                .invoke("sendErrorResponse")
                                                .withArgumentFromInvoking("getMessage")
                                                .on("failure")
                                                .withArgumentFromInvoking("status")
                                                .on("((ResponseException) failure)")
                                                .withArgument("ctx")
                                                .inScope();

                                        test = test.elseIf().variable("failure").instanceOf("UnsupportedOperationException")
                                                .lineComment("Used by the generated mock implementations to indicate a real")
                                                .lineComment("implementation was not bound, and should be translated to a")
                                                .lineComment("501 Not Implemented response.")
                                                .invoke("sendErrorResponse")
                                                .withArgumentFromInvoking("getMessage")
                                                .on("failure")
                                                .withArgument(501)
                                                .withArgument("ctx")
                                                .inScope();

                                        test.orElse().invoke("next").on("ctx").endIf();
                                    });
                                }).on("rtr");
                    });
                }).on(verticleBuilderName).endIf();
    }

    private void generateCheckTypeMethod(ClassBuilder<String> cb) {
        cb.method("checkType")
                .docComment("Used by binding methods to ensure valid types are bound, and only "
                        + "when the module is not yet initialized."
                        + "\n@param type The type being proposed for binding"
                        + "\n@param actualType The type it must be compatible with"
                        + "\nreturn the actualType paramater"
                        + "\n@throws IllegalArgumentException if the type is null, abstract, "
                        + "an interface, or not actually a subtype of <code>actualType</code>")
                .withModifier(PRIVATE)
                .withTypeParam("T")
                .addArgument("Class<? extends T>", "type")
                .addArgument("Class<T>", "actualType")
                .returning("Class<? extends T>")
                .body(bb -> {
                    bb.invoke("checkInitialized").inScope();
                    bb.ifNull("type")
                            .andThrow()
                            .withStringLiteral("Type may not be null")
                            .ofType("IllegalArgumentException");
                    Value abstractTest = invocationOf("getModifiers")
                            .on("type").and("java.lang.reflect.Modifier.ABSTRACT")
                            .parenthesized().isNotEqualTo(number(0)
                                    .logicalOrWith(invocationOf("isInterface").on("type")));

                    bb.iff(abstractTest)
                            .andThrow(nb -> nb.withStringLiteral("Cannot bind an abstract class or interface")
                            .ofType("IllegalArgumentException")).endIf();
                    bb.iff(invocationOf("isAssignableFrom").withArgument("type").on("actualType").isEqualTo("false"))
                            .andThrow(nb -> nb.withStringConcatentationArgument("Not really a subclass of ")
                            .appendInvocationOf("getSimpleName")
                            .on("actualType")
                            .endConcatenation()
                            .ofType("IllegalArgumentException")).endIf();
                    bb.returning("type");
                });
    }

    public void generateErrorHandlingDisablementMethodAndField(ClassBuilder<String> routerBuilder) {
        routerBuilder.field("defaultErrorHandling")
                .withModifier(PRIVATE)
                .initializedWith(true);

        routerBuilder.method("disableDefaultErrorHandling")
                .withModifier(PUBLIC)
                .returning(routerBuilder.className())
                .docComment("By default, various exception types are translated into appropriate "
                        + "error response codes with string messages.  If you want to do your own, "
                        + "call this method and attach an error handler to the router."
                        + "\n@return this")
                .body(bb -> {
                    bb.invoke("checkInitialized").inScope();
                    bb.statement("defaultErrorHandling = false");
                    bb.returningThis();
                });
    }

    public void generateSendErrorResponseMethod(ClassBuilder<String> routerBuilder) {
        routerBuilder.method("sendErrorResponse", m1 -> {
            m1.docComment("Used by the default error handling code to send an error "
                    + "response."
                    + "\n@param message The exception message or null"
                    + "\n@param statusCode The http status code"
                    + "\n@param ctx The routing context");
            routerBuilder.importing("io.vertx.ext.web.RoutingContext");
            m1.withModifier(PRIVATE, STATIC, FINAL);
            m1.addArgument("String", "message")
                    .addArgument("int", "statusCode")
                    .addArgument("RoutingContext", "ctx");
            m1.body(mbb -> {
                mbb.ifNull("message")
                        .invoke("send")
                        .onInvocationOf("setStatusCode")
                        .withArgument("statusCode")
                        .onInvocationOf("response")
                        .on("ctx")
                        .orElse()
                        .invoke("send")
                        .withArgument("message")
                        .onInvocationOf("setStatusCode")
                        .withArgument("statusCode")
                        .onInvocationOf("response")
                        .on("ctx")
                        .endIf();
            });
        });
    }

    private String operationPackage(OperationShape op) {
        return packageOf(names().packageOf(op)) + ".impl";
    }

    private String generateOperationAuth(OperationShape op, ResourceGraph graph,
            String implPackage, AuthenticatedTrait auth, SpiTypesAndArgs spiArgs,
            List<String> handlers, Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage)
                .named(escape(op.getId().getName() + "Authentication"))
                .implementing("Handler<RoutingContext>")
                .withModifier(PUBLIC, FINAL)
                .docComment("Authentication implementation for the operation " + op.getId().getName()
                        + ". Authenticates as specified in that operation, calling the appropriate authenticator interface, "
                        + "makes it available for injection in the request scope and invokes next() on the "
                        + "RoutingContext asynchronously.");
        initDebug(cb);
        handlers.add(cb.fqn());
        String authPackage = OperationNames.authPackage(shape, names());
        Shape authTarget = model.expectShape(auth.getPayload());
        String authInterface = authenticateWithInterfaceName(auth.getPayload());

        String authTargetFqn = names().qualifiedNameOf(authTarget, cb, true);
        String authTargetName = typeNameOf(authTarget);
        String authEnumTypeName = serviceAuthenticatedOperationsEnumName(shape);

        if (auth.isOptional()) {
            spiArgs.add("java.util.Optional<" + authTargetName + ">", "auth", true);
            spiArgs.alsoImport(authTargetFqn);
        } else {
            spiArgs.add(authTargetFqn, "auth", true);
        }

        scope.bindDirect(authPackage + "." + authEnumTypeName,
                "Used by " + typeNameOf(op));
        if (auth.isOptional()) {
            scope.bindOptional(authTargetFqn, "Optional auth target of " + typeNameOf(op));
        } else {
            scope.bindDirect(authTargetFqn, "Auth target of " + typeNameOf(op));
        }

        maybeImport(cb,
                authPackage + "." + authEnumTypeName,
                authTargetFqn
        );

        String authEnumConstantName = authEnumTypeName + "."
                + TypeNames.enumConstantName(op.getId().getName());

        maybeImport(cb, authTargetFqn,
                authPackage + "." + authInterface,
                "javax.inject.Inject",
                "io.vertx.core.Handler",
                "io.vertx.ext.web.RoutingContext",
                "java.util.Optional",
                "com.mastfrog.smithy.http.AuthenticationResultConsumer",
                "static com.telenav.smithy.vertx.adapter.VertxRequestAdapter.smithyRequest",
                "com.telenav.vertx.guice.scope.RequestScope");
        cb.field("authenticator")
                .withModifier(PRIVATE, FINAL)
                .ofType(authInterface);
        cb.field("scope")
                .withModifier(PRIVATE, FINAL)
                .ofType("RequestScope");

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument(authInterface, "authenticator")
                    .addArgument("RequestScope", "scope")
                    .body(bb -> {
                        bb.statement("this.authenticator = authenticator");
                        bb.statement("this.scope = scope");
                    });
        });
        cb.overridePublic("handle", mth -> {
            mth.addArgument("RoutingContext", "event");
            mth
                    .body(bb -> {
                        cb.importing(CompletableFuture.class
                        );
                        bb.declare("fut")
                                .initializedWithNew().ofType("CompletableFuture<>")
                                .as("CompletableFuture<" + authTargetName + ">");
                        bb.invoke("whenCompleteAsync")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("user")
                                            .withArgument("thrown");
                                    lb.body(lbb -> {
                                        wrapInTryCatch("event", lbb, tri -> {
                                            generateAuthFutureCompletion(tri, auth,
                                                    authEnumTypeName, authEnumConstantName);
                                        });
                                    });
                                })
                                .withArgumentFromInvoking("nettyEventLoopGroup")
                                .onInvocationOf("vertx")
                                .on("event")
                                .on("fut");

                        bb.invoke("authenticate")
                                .withArgument(authEnumConstantName)
                                .withArgumentFromInvoking("smithyRequest")
                                .withArgumentFromInvoking("request")
                                .on("event")
                                .inScope()
                                .withArgument(auth.isOptional())
                                .withArgumentFromInvoking("create")
                                .withArgument("fut")
                                .withArgument(auth.isOptional())
                                .on("AuthenticationResultConsumer")
                                .on("authenticator");
                    });
        });

        addTo.accept(cb);
        return authPackage + "." + authInterface;
    }

    public <C, X, B extends BlockBuilderBase<C, B, X>> void generateAuthFutureCompletion(B bb, AuthenticatedTrait auth,
            String authEnumTypeName, String authEnumConstantName) {

        ElseClauseBuilder<B> els = bb.ifNotNull("thrown")
                .lineComment("If authentication has failed, abort here and let")
                .lineComment("the verticle's failure handler translate the exception into an")
                .lineComment("appropriate http response")
                .invoke("fail")
                .withArgument("thrown")
                .on("event")
                .orElse()
                .lineComment("Authentication succeeded.  Enqueue the actual processing of the")
                .lineComment("request, injecting the authentication result into the request scope")
                .lineComment("so that the next handler can take it as a constructor argument");

        InvocationBuilder<InvocationBuilder<ElseClauseBuilder<B>>> partialInvoke = els.invoke("submit")
                .withArgumentFromInvoking("wrap")
                .withMethodReference("next").on("event");
        if (auth.isOptional()) {
            partialInvoke = partialInvoke.withArgumentFromInvoking("ofNullable")
                    .withArgument("user")
                    .on("Optional");
        } else {
            partialInvoke = partialInvoke.withArgument("user");
        }
        partialInvoke
                .withArgument(authEnumConstantName)
                .on("scope")
                .onInvocationOf("nettyEventLoopGroup")
                .onInvocationOf("vertx")
                .on("event")
                .endIf();
    }

    private ClassBuilder<String> generateOperation(OperationShape op,
            ResourceGraph graph, List<String> handlers, Consumer<ClassBuilder<String>> others) {
        String implPackage = operationPackage(op);
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage)
                .named(escape(op.getId().getName()))
                .docComment("Handles assembling the input for the "
                        + op.getId().getName() + " operation.")
                .importing(
                        "io.vertx.core.Handler",
                        "io.vertx.ext.web.RoutingContext",
                        "com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse",
                        "com.telenav.vertx.guice.scope.RequestScope",
                        "static com.telenav.smithy.vertx.adapter.VertxRequestAdapter.smithyRequest",
                        "javax.inject.Inject",
                        OperationNames.operationInterfaceFqn(model, op)
                )
                .implementing("Handler<RoutingContext>")
                .withModifier(PUBLIC, FINAL);

        SpiTypesAndArgs spiArgs = new SpiTypesAndArgs();

        spiArgs.add("com.mastfrog.smithy.http.SmithyRequest", "smithyRequest");

        initDebug(cb);
        cb.field("spi")
                .withModifier(FINAL, PRIVATE)
                .ofType(OperationNames.operationInterfaceName(op));

        StructureShape in = inputOrNull(op);
        Input input = in == null ? null : examineInput(op, in, graph, cb);

        if (input != null) {
            scope.bindDirect(input.fqn(), "Input for " + op.getId().getName());
        }

        cb.blockComment("INPUT: " + input);

        boolean readsPayload = input != null && (input.httpPayloadType() != null)
                || (input != null && input.isEmpty());
        boolean writesPayload = op.getOutput().isPresent();

        if (readsPayload) {
            cb.importing("com.fasterxml.jackson.databind.ObjectMapper");
            cb.field("mapper")
                    .withModifier(FINAL, PRIVATE)
                    .ofType("ObjectMapper");
        }

        op.getTrait(AuthenticatedTrait.class
        )
                .map(auth -> {
                    return generateOperationAuth(op,
                            graph, implPackage, auth, spiArgs,
                            handlers, others);
                }
                ).orElse(null);

        cb.field("scope", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .ofType("RequestScope");
        });

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument(OperationNames.operationInterfaceName(op), "spi");
            con.addArgument("RequestScope", "scope");
            if (readsPayload) {
                con.addArgument("ObjectMapper", "mapper");
            }
            con.body(bb -> {
                bb.assignField("spi").ofThis().toExpression("spi");
                bb.assignField("scope").ofThis().toExpression("scope");

                if (readsPayload) {
                    bb.assignField("mapper").ofThis().toExpression("mapper");
                }
            });
        });

        cb.overridePublic("handle", mth -> {
            mth.addArgument("RoutingContext", "context")
                    .body(bb -> {
                        bb.declare("smithyRequest")
                                .initializedByInvoking("smithyRequest")
                                .withArgumentFromInvoking("request")
                                .on("context")
                                .inScope()
                                .as("SmithyRequest");
                        String inputVar = gatherInput(in, op, input, bb, cb, spiArgs);
                        // XXX need to test something that has an empty response

                        if (!writesPayload) {
                            int responseCode = op.getTrait(HttpTrait.class
                            )
                                    .map(http -> http.getCode()).orElse(200);
                            bb.invoke("send")
                                    .onInvocationOf("setStatusCode")
                                    .withArgument(responseCode)
                                    .onInvocationOf("response")
                                    .on("context");
                        }

                    });
        });
        handlers.add(cb.fqn());
        if (readsPayload) {
            generateWithContentMethod(cb);
        }
        if (writesPayload) {
            generateOutputWritingHandler(op, implPackage, input, spiArgs, handlers, others);
//            generateWriteOutputMethod(cb);
        }
        return cb;
    }

    private void generateOutputWritingHandler(OperationShape op, String implPackage, Input input, SpiTypesAndArgs spiArgs, List<String> handlers, Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage)
                .named(escape(op.getId().getName() + "ResponseEmitter"))
                .docComment("Handles computing and sending the response for the "
                        + op.getId().getName() + " operation.")
                .importing(
                        "io.vertx.core.Handler",
                        "io.vertx.ext.web.RoutingContext",
                        "com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse",
                        "static com.telenav.smithy.vertx.adapter.VertxRequestAdapter.smithyRequest",
                        "javax.inject.Inject",
                        "com.telenav.vertx.guice.scope.RequestScope",
                        input.fqn(),
                        operationInterfaceFqn(model, op)
                )
                .implementing("Handler<RoutingContext>")
                .withModifier(PUBLIC, FINAL);
        initDebug(cb);
        handlers.add(cb.fqn());
        spiArgs.importTypes(cb);
        cb
                .importing(CompletableFuture.class
                );
        boolean writesPayload = op.getOutput().isPresent();

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation()
                    .addArgument(operationInterfaceName(op), "spi")
                    .addArgument(simpleNameOf(input.typeName()), "input")
                    .body(bb -> {
                        cb.field("input", inField -> {
                            inField.withModifier(PRIVATE, FINAL)
                                    .ofType(simpleNameOf(input.typeName()));
                        });
                        cb.field("spi", spiField -> {
                            spiField.withModifier(PRIVATE, FINAL)
                                    .ofType(operationInterfaceName(op));
                        });
                        bb.statement("this.input = input");
                        bb.statement("this.spi = spi");
                        if (writesPayload) {
                            cb.importing("com.fasterxml.jackson.databind.ObjectMapper");
                            cb.field("mapper", mapField -> {
                                mapField.withModifier(PRIVATE, FINAL)
                                        .ofType("ObjectMapper");
                            });
                            con.addArgument("ObjectMapper", "mapper");
                            bb.statement("this.mapper = mapper");
                        }
                        spiArgs.eachInjectableType((fqn, varName) -> {
                            // Need to strip generics
                            cb.importing(rawTypeName(fqn));
                            con.addArgument(simpleNameOf(fqn), varName);
                            cb.field(varName, fld -> {
                                fld.withModifier(PRIVATE, FINAL);
                                fld.ofType(simpleNameOf(fqn));
                            });
                            bb.statement("this." + varName + " = " + varName);
                        });
                    });
        });

        cb.overridePublic("handle", mth -> {
            mth.addArgument("RoutingContext", "context");
            mth.body(bb -> {
                bb.declare("smithyRequest")
                        .initializedByInvoking("smithyRequest")
                        .withArgumentFromInvoking("request")
                        .on("context")
                        .inScope()
                        .as("SmithyRequest");
                generateResponseHandling(bb, op, cb, spiArgs);
            });
        });
        if (writesPayload) {
            generateWriteOutputMethod(cb);
        }
        addTo.accept(cb);

    }

    private <C, B extends BlockBuilderBase<C, B, ?>> String gatherInput(StructureShape in, OperationShape op, Input input, B bb, ClassBuilder<String> cb, SpiTypesAndArgs spiArgs) {

        if (in != null) {
            Obj<String> ret = Obj.create();
            if (input.httpPayloadType() != null || input.isEmpty()) {
                cb.importing(input.fqn());
                input.applyImports(cb);
                String inputType = input.isEmpty() ? input.typeName() : input.httpPayloadType();

                scope.bindDirect(input.fqn(), "Input type for " + typeNameOf(op)
                        + " consumed by " + OperationNames.operationInterfaceName(op));

                bb.invoke("withContent")
                        .withClassArgument(inputType)
                        .withArgument("context")
                        .withLambdaArgument(lb -> {
                            lb.withArgument("payload")
                                    .body(lbb -> {
                                        ret.set(assembleOperationInput(lbb, op, input, cb, "payload", spiArgs));
                                    });
                        }).inScope();
            } else {
                ret.set(assembleOperationInput(bb, op, input, cb, "payload", spiArgs));
            }
            return ret.get();
        } else {
            bb.lineComment("Do the thing.");
            invokeNextAsync(bb, null);
        }
        return null;
    }

    private <C, B extends BlockBuilderBase<C, B, ?>> void invokeNextAsync(B bb, String inputVar) {
        InvocationBuilder<InvocationBuilder<B>> partialInvoke = bb.invoke("submit")
                .withArgumentFromInvoking("wrap")
                .withMethodReference("next").on("context");

        if (inputVar != null) {
            partialInvoke = partialInvoke.withArgument(inputVar);
        }
        partialInvoke
                .on("scope")
                .onInvocationOf("nettyEventLoopGroup")
                .onInvocationOf("vertx")
                .on("context");

    }

    public <C, B extends BlockBuilderBase<C, B, ?>> void generateResponseHandling(B bb,
            OperationShape op, ClassBuilder<?> cb, SpiTypesAndArgs spiArgs) {
        cb.importing(CompletableFuture.class
        );
        cb.importing(
                "static com.telenav.smithy.vertx.adapter.VertxResponseCompletableFutureAdapter.smithyResponse"
        );
        spiArgs.add("com.mastfrog.smithy.http.SmithyResponse", "response");
        String outputTypeName = op.getOutput().map(outId -> {
            Shape outShape = model.expectShape(outId);
            String result = typeNameOf(outShape);
            maybeImport(cb, names().packageOf(outShape) + "." + result);
            return result;
        }).orElse("Void");
        bb.lineComment("The SPI takes a CompletableFuture to be framework-independent");
        bb.declare("fut")
                .initializedWithNew(nb -> nb.ofType("CompletableFuture<>"))
                .as("CompletableFuture<" + outputTypeName + ">");
        bb.declare("response")
                .initializedByInvoking("smithyResponse")
                .withArgument("context")
                .withArgument("fut")
                .inScope()
                .as("SmithyResponse<" + outputTypeName + ">");
        bb.trying(tri -> {
            ClassBuilder.InvocationBuilder<?> inv = tri.invoke("respond");
            for (String arg : spiArgs.args) {
                inv = inv.withArgument(arg);
            }
            inv.on("spi");
            tri.catching(cat -> {
                cat.blankLine().lineComment("The SPI can throw an exception which the outer")
                        .lineComment("error handler will translate into an appropriate response.");
                cat.invoke("fail")
                        .withArgument("thrown")
                        .on("context");
                cat.statement("return");
            }, "Exception");
        });
        bb.invoke("whenCompleteAsync")
                .withLambdaArgument(lb -> {
                    lb.withArgument("output")
                            .withArgument("thrown")
                            .body(lbb -> {
                                lbb.lineComment("Ensure that anything that goes wrong results")
                                        .lineComment("in SOME response; otherwise the request would")
                                        .lineComment("hang.");
                                wrapInTryCatch("context", lbb, tri -> {
                                    tri.ifNotNull("thrown")
                                            .invoke("fail")
                                            .withArgument("thrown")
                                            .on("context")
                                            .orElse()
                                            .invoke("writeOutput")
                                            .withArgument("output")
                                            .withArgument("context")
                                            .inScope()
                                            .endIf();
                                });
                            });
                })
                .withArgumentFromInvoking("nettyEventLoopGroup")
                .onInvocationOf("vertx")
                .on("context")
                .on("fut");
    }

    public <C, B extends BlockBuilderBase<C, B, ?>> String assembleOperationInput(B bb, OperationShape op, Input input, ClassBuilder<String> cb, String payloadVar, SpiTypesAndArgs spiArgs) {
        cb.importing(input.fqn());
        if (input.httpPayloadType() != null && input.size() == 1) {
            spiArgs.add(input.fqn(), "input");
            scope.bindDirect(input.fqn(), "Input payload of " + op.getId().getName());
            invokeNextAsync(bb, payloadVar);
            return payloadVar;
        } else if (input.isEmpty()) {
            spiArgs.add(input.fqn(), "input");
            scope.bindDirect(input.fqn(), "Input payload of " + op.getId().getName());
            invokeNextAsync(bb, payloadVar);
            return payloadVar;
        }

        bb.lineComment("Have input " + input);
        List<String> inputVariables = new ArrayList<>();
        for (InputMemberObtentionStrategy strat : input) {
            bb.lineComment("IMOS: " + strat);
            if (strat.type() == OriginType.HTTP_PAYLOAD) {
                inputVariables.add(payloadVar);
            } else {
                strat.comment(bb);
                inputVariables.add(strat.generateObtentionCode(cb, bb));
            }
        }
        bb.lineComment("Input vars: " + inputVariables);
        String nm = input.typeName();
        bb.declare("input").initializedWithNew(nb -> {
            for (String arg : inputVariables) {
                nb.withArgument(arg);
            }
            nb.ofType(nm);
        }).as(nm);
        spiArgs.add(input.fqn(), "input");

        invokeNextAsync(bb, "input");
        return "input";
    }

    static <C, X, B extends BlockBuilderBase<C, B, X>> void wrapInTryCatch(
            String ctxVar, B bb, Consumer<TryBuilder<B>> consumer) {
        TryBuilder<B> tri = bb.trying();
        consumer.accept(tri);
        tri.catching(cat -> {
            cat.lineComment("We need to ensure " + ctxVar + ".fail() is called on")
                    .lineComment("failure no matter WHAT happens.");
            cat.as("ex").invoke("fail").withArgument("ex").on(ctxVar);
        }, "Exception", "Error");
    }

    private <C> void generateWriteOutputMethod(ClassBuilder<C> cb) {
        cb.importing(
                "com.fasterxml.jackson.core.JsonProcessingException",
                "io.vertx.core.Future",
                "static io.vertx.core.buffer.Buffer.buffer"
        );
        cb.method("writeOutput", mth -> {
            mth.docComment("Write an object to the response using jackson."
                    + "\n@param output the output object or null"
                    + "\n@param context the context we are writing to"
                    + "\n@return the future returned by the write operation");
            mth.withModifier(PRIVATE)
                    .withTypeParam("T")
                    .addArgument("T", "output")
                    .addArgument("RoutingContext", "context")
                    .returning("Future<Void>");
            mth.body(bb -> {
                bb.ifNull("output")
                        .returningInvocationOf("send")
                        .onInvocationOf("response")
                        .on("context").endIf();
                bb.trying(tri -> {
                    tri.returningInvocationOf("send")
                            .withArgumentFromInvoking("buffer")
                            .withArgumentFromInvoking("writeValueAsBytes")
                            .withArgument("output")
                            .on("mapper")
                            .inScope()
                            .onInvocationOf("response")
                            .on("context");
                    tri.catching(cat -> {
                        cat.invoke("fail")
                                .withArgument("thrown")
                                .on("context");
                        cat.returningInvocationOf("failedFuture")
                                .withArgument("thrown")
                                .on("Future");
                    }, "JsonProcessingException");
                });
            });
        });
    }

    private <C> void generateWithContentMethod(ClassBuilder<C> cb) {
        cb.importing(
                "io.netty.buffer.ByteBufInputStream",
                validationExceptions().fqn(),
                "java.io.IOException",
                "java.io.InputStream"
        );
        cb.importing(Consumer.class);
        cb.method("withContent", mth -> {
            mth.withModifier(PRIVATE)
                    .withTypeParam("T")
                    .addArgument("Class<T>", "type")
                    .addArgument("RoutingContext", "context")
                    .addArgument("Consumer<T>", "consumer");
            mth.body(bb -> {
                bb.declare("input").as("T");
                bb.trying(tri -> {
                    tri.declare("stream")
                            .initializedWithNew(nb -> {
                                nb.withArgumentFromInvoking("getByteBuf")
                                        .onInvocationOf("buffer")
                                        .onInvocationOf("body")
                                        .on("context")
                                        .ofType("ByteBufInputStream");
                            }).as("InputStream");
                    tri.trying(innerTry -> {
                        innerTry.assign("input")
                                .toInvocation("readValue")
                                .withArgument("stream")
                                .withArgument("type")
                                .onField("mapper").ofThis();
                        innerTry.fynalli(fi -> fi.invoke("close").on("stream"));
                    });
                    tri.catching(cat -> {
                        cat.invoke("fail")
                                .withArgument(400)
                                .withArgument("thrown")
                                .on("context");
                        cat.statement("return");
                    }, "IOException", validationExceptions().name());
                });
                bb.invoke("accept")
                        .withArgument("input")
                        .on("consumer");
            });

        });
    }

    private <B> void invokeRoute(BlockBuilder<B> bb, String verticleBuilderName,
            ClassBuilder<String> routerBuilder, List<String> handlers,
            OperationShape op) {

        handlers.forEach(routerBuilder::importing);
        InvocationBuilder<BlockBuilder<B>> inv = null;
        if (handlers.size() == 1) {
            inv = bb.invoke("handledBy")
                    .withClassArgument(handlers.get(0));
        } else {
            // We need to iterate in reverse order, since we start
            // with the last thing invoked and work backwards - as in
            // invoke("last").onInvocationOf("middle").onInvocationOf("first")
            // gets you first().middle().last().
            for (int i = handlers.size() - 1; i >= 0; i--) {
                String handler = handlers.get(i);
                if (i == handlers.size() - 1) {
                    inv = bb.invoke("terminatedBy")
                            .withClassArgument(simpleNameOf(handler));
                } else {
                    inv = inv.onInvocationOf("withHandler")
                            .withClassArgument(simpleNameOf(handler));
                }
            }
        }

        Optional<HttpTrait> httpOpt = op.getTrait(HttpTrait.class);
        if (!httpOpt.isPresent()) {
            throw new Error("No @http on " + op);
        }
        HttpTrait http = httpOpt.get();

        PathInfo info = new PathInformationExtractor(model, http.getUri())
                .withLeadingSlashInRegex()
                .extractPathInfo(inputOrNull(op));

        if (info.isRegex()) {
            inv = inv.onInvocationOf("withRegex").withStringLiteral(info.text());
        } else {
            inv = inv.onInvocationOf("withPath").withStringLiteral(info.text());
        }

        inv = inv.onInvocationOf("forHttpMethod")
                .withArgument("HttpMethod." + http.getMethod().toUpperCase());

        inv.onInvocationOf("route").on(verticleBuilderName);
    }

    private StructureShape inputOrNull(OperationShape op) {
        return op.getInput().flatMap(id -> {
            Shape sh = model.expectShape(id);
            return sh.asStructureShape();
        }).orElse(null);
    }

    private Input examineInput(OperationShape op, StructureShape input, ResourceGraph graph,
            ClassBuilder<String> cb) {
        Optional<HttpTrait> httpOpt = op.getTrait(HttpTrait.class
        );
        List<InputMemberObtentionStrategy> st = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(" ************** STRATEGIES **************");
        sb.append("\n Input ").append(typeNameOf(input)).append(" - ").append(input.getId());
        sb.append("\n has @http? ").append(httpOpt);
        if (httpOpt.isPresent()) {
            HttpTrait http = httpOpt.get();
            UriPattern pattern = http.getUri();

            for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {

                MemberShape m = e.getValue();
                Shape memberTarget = model.expectShape(m.getTarget());

                sb.append("\n  * ").append(e.getKey()).append(" ").append(typeNameOf(memberTarget))
                        .append(" - ").append(memberTarget.getType());

                // Ensure the member type is imported
                names().typeNameOf(cb, memberTarget, false);

                if (m.getTrait(HttpPayloadTrait.class).isPresent()) {
                    sb.append(" - payload");
                    StructureShape payloadShape = model.expectShape(m.getTarget(), StructureShape.class);
                    String fqn = names().packageOf(payloadShape) + "."
                            + typeNameOf(payloadShape);

                    maybeImport(cb, fqn);
                    st.add(new InputMemberObtentionStrategy(new PayloadOrigin(fqn), payloadShape, m, names()));
                } else if (m.getTrait(HttpLabelTrait.class).isPresent()) {
                    HttpLabelTrait t = m.expectTrait(HttpLabelTrait.class);
                    String name = m.getMemberName();
                    // XXX check the trait that can provide an alternate name

                    List<SmithyPattern.Segment> segs = pattern.getSegments();
                    int ix = -1;
                    for (int i = 0; i < segs.size(); i++) {
                        SmithyPattern.Segment seg = segs.get(i);
                        if (seg.isLabel()) {
                            if (seg.getContent().equals(name)) {
                                ix = i;
                                break;
                            }
                        }
                    }

                    sb.append(" - label ").append(ix).append(" - ").append(t.toShapeId());

                    RequestParameterOrigin uq = new RequestParameterOrigin(Integer.toString(ix), URI_PATH, declarationFor(URI_PATH, memberTarget, m, model, cb));

                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpQueryTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    RequestParameterOrigin uq = new RequestParameterOrigin(name, URI_QUERY,
                            declarationFor(URI_QUERY, memberTarget, m, model, cb));

                    sb.append(" - query ").append(name);
                    // XXX check the trait that can provide an alternate name
                    st.add(
                            new InputMemberObtentionStrategy(uq,
                                    model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpHeaderTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    HttpHeaderTrait trait = m.getTrait(HttpHeaderTrait.class).get();

                    sb.append(" - header ").append(trait.getValue());

                    RequestParameterOrigin uq = new RequestParameterOrigin(trait.getValue(), HTTP_HEADER,
                            declarationFor(HTTP_HEADER, memberTarget, m, model, cb));
                    // XXX check the trait that can provide an alternate name

                    st.add(
                            new InputMemberObtentionStrategy(uq,
                                    model.expectShape(m.getTarget()), m, names()));
                }
            }
        } else {
            sb.append("\n NO @http present");
//            throw new UnsupportedOperationException("HTTP input inferencing not implemented yet.");
        }
        cb.blockComment(sb.toString());
//        if (st.isEmpty()) {
//            String fqn = names().packageOf(shape) + "." + TypeNames.typeNameOf(shape);
//            InputMemberObtentionStrategy strat = new InputMemberObtentionStrategy(new PayloadOrigin(fqn), shape, null);
//            st.add(strat);
//        }
        return new Input(input, st, op, names());

    }

    <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declaration<?, ?, ?, ?>
            declarationFor(OriginType type, Shape memberTarget, MemberShape member, Model model,
                    ClassBuilder<?> cb) {

        boolean required = member.getTrait(RequiredTrait.class).isPresent();
        Optional<DefaultTrait> def = member.getTrait(DefaultTrait.class)
                .or(() -> memberTarget.getTrait(DefaultTrait.class));

        boolean isModelType = !"smithy.api".equals(memberTarget.getId().getNamespace());

        Declarer<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>> decl;
        if (def.isPresent()) {
            decl = Declarer.<B, Tr, Rr>withDefaultFor(def.get(), memberTarget, model);
        } else if (required) {
            String[] fqns = new String[]{validationExceptions().fqn()};
            maybeImport(cb, fqns);
            decl = Declarer.<B, Tr, Rr>orThrow(validationExceptions().name());
        } else {
            decl = Declarer.<B, Tr, Rr>nullable();
        }
        return applyDeclaration(decl, type, required, isModelType, memberTarget, member, model, cb);
    }

    static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends ClassBuilder.InvocationBuilderBase<ClassBuilder.TypeAssignment<B>, Ir>>
            Declaration<B, Tr, Rr, ?>
            applyDeclaration(Declarer<B, Tr, Rr, Ir> dec, OriginType type, boolean required, boolean isModelType,
                    Shape memberTarget, MemberShape member, Model model, ClassBuilder<?> cb) {

        TypeNames tn = new TypeNames(model);
        Declarer<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>> res;
        switch (memberTarget.getType()) {
            case INTEGER:
                res = dec.with(originMethodCastTo(Integer.class));
                break;
            case LONG:
                res = dec.with(originMethodCastTo(Long.class));
                break;
            case SHORT:
                res = dec.with(originMethodCastTo(Short.class));
                break;
            case BYTE:
                res = dec.with(originMethodCastTo(Byte.class));
                break;
            case FLOAT:
                res = dec.with(originMethodCastTo(Float.class));
                break;
            case DOUBLE:
                res = dec.with(originMethodCastTo(Double.class));
                break;
            case STRING:
                String tp = tn.qualifiedNameOf(memberTarget, cb, false);
                maybeImport(cb, tp);
                res = dec.with(mapToNewFromString(
                        typeNameOf(memberTarget)))
                        .with(originMethod());
                break;
            case BIG_DECIMAL:
                res = dec.with(mapToBigDecimal())
                        .with(originMethod());
                break;
            case BIG_INTEGER:
                res = dec.with(mapToBigInteger())
                        .with(originMethod());
                break;
            case LIST:
                res = dec.with(splitToMappedCollection(memberTarget, model))
                        .with(originMethod());
                break;
            case SET:
                res = dec.with(splitToStringSet())
                        .with(originMethod());
                break;
            case BOOLEAN:
                res = dec.with(mapToBoolean())
                        .with(originMethod());
                break;
            case TIMESTAMP:
                res = dec.with(mapToTimestamp())
                        .with(originMethod());
                break;
            case INT_ENUM:
                maybeImport(cb, tn.qualifiedNameOf(memberTarget, cb, false));
                res = dec.with(mapToIntEnum(typeNameOf(memberTarget)))
                        .with(originMethod());
                break;
            default:
                throw new UnsupportedOperationException("Not implemented: " + memberTarget.getType() + " " + memberTarget);
        }
        boolean requiredOrHasDefault = requiredOrHasDefault(member, memberTarget);
        return res.closedWith(DeclarationClose.onRequest(typeNameOf(memberTarget, requiredOrHasDefault)));
    }

    private void generateCustomizeVerticleFieldsAndMethods(ClassBuilder<String> cb) {
        cb.field("verticleConfigurer")
                .withModifier(PRIVATE)
                .initializedAsLambda("Consumer<VerticleBuilder<VertxGuiceModule>>", lb -> lb.withArgument("ignored").emptyBody());
        cb.method("configuringVerticleWith")
                .docComment("This module installs http handlers in a single verticle; the configuration "
                        + "of that verticle can be customized with a consumer passed here, which will be called "
                        + "with the VerticleBuilder during initialization.  This method can be called multiple times "
                        + "and all consumers will be run in sequence."
                        + "\n@param consumer a consumer"
                        + "\n@return this")
                .addArgument("Consumer<? super VerticleBuilder<VertxGuiceModule>>", "consumer")
                .withModifier(PUBLIC)
                .returning(cb.className())
                .body(bb -> {
                    bb.invoke("checkInitialized").inScope();
                    bb.ifNull("consumer")
                            .andThrow(nb -> nb.withStringLiteral("Consumer may not be null").ofType("IllegalArgumentException"));
                    bb.assignField("verticleConfigurer")
                            .ofThis()
                            .toInvocation("andThen")
                            .withArgument("consumer")
                            .onField("verticleConfigurer")
                            .ofThis();
                    bb.returning("this");
                });
    }

    private void generateMainMethod(ClassBuilder<String> cb) {
        cb.method("main", mth -> {
            mth.addArgument("String[]", "args")
                    .docComment("A simple main method which will launch the server with no "
                            + "handlers installed to prove it works."
                            + "\n@param args ignored")
                    .withModifier(PUBLIC, STATIC)
                    .body(bb -> {
                        bb.invoke("start")
                                .onInvocationOf("configuringVerticleWith")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("verticleBuilder")
                                            .body(lbb -> {
                                                lbb.invoke("withPort")
                                                        .withArgument(8123)
                                                        .on("verticleBuilder");
                                            });
                                })
                                .onNew(nb -> {
                                    nb.ofType(cb.className());
                                });
                    });
        });
    }

    interface AuthInfoConsumer {

        void authInfo(Shape payload, String mechanism, String pkg, String payloadType, boolean optional);
    }

    private void withAuthInfo(OperationShape shape, AuthInfoConsumer c) {
        withAuthInfo(shape, model, names(), c);
    }

    public static void withAuthInfo(OperationShape shape, Model model, TypeNames names, AuthInfoConsumer c) {
        shape.getTrait(AuthenticatedTrait.class
        ).ifPresent(auth -> {
            Shape payload = model.expectShape(auth.getPayload());
            String pkg = names.packageOf(payload);
            String nm = typeNameOf(payload);

            c.authInfo(payload, auth.getMechanism(), pkg, nm, auth.isOptional());
        });
    }

    private <C, B extends BlockBuilderBase<C, B, ?>> void generateAuthBindingMethodsAndCalls(B bb, ClassBuilder<?> cb, Set<OperationShape> ops) {
        Set<String> mechanisms = new TreeSet<>();
        Bool hasOptional = Bool.create();
        Map<ShapeId, Set<OperationShape>> operationsForPayload = new HashMap<>();
        Set<ShapeId> allPayloadTypes = new HashSet<>();

        for (Shape opId : ops) {
            OperationShape op = opId.asOperationShape().get();
            op.getTrait(AuthenticatedTrait.class)
                    .ifPresent(authTrait -> {
                        mechanisms.add(authTrait.getMechanism().toLowerCase());
                        hasOptional.or(authTrait::isOptional);
                        allPayloadTypes.add(authTrait.getPayload());
                        operationsForPayload.computeIfAbsent(authTrait.getPayload(),
                                p -> new HashSet<>()).add(op);
                    });
        }
        if (mechanisms.isEmpty()) {
            return;
        }
        String pkg = authPackage(shape, names());
        for (ShapeId id : allPayloadTypes) {
            String nm = authenticateWithInterfaceName(id);
            cb.importing(pkg + "." + nm);

            String fieldName = decapitalize(nm) + "Type";
            String argName = "type";
            String classType = "Class<? extends " + nm + ">";
            String methodName = decapitalize(nm) + "Using";

            bb.ifNotNull(fieldName)
                    .invoke("to")
                    .withArgument(fieldName)
                    .onInvocationOf("bind")
                    .withClassArgument(nm)
                    .on("binder")
                    .endIf();

            cb.field(fieldName, fld -> {
                fld.withModifier(PRIVATE)
                        .ofType(classType);
            });

            cb.method(methodName, mth -> {
                mth.addArgument(classType, argName)
                        .withModifier(PUBLIC)
                        /// Pending - list the operations using this interface
                        .docComment("Bind an authenticator for use in authenticating.  An implementation <b>must</b> be bound "
                                + "for the operations which use this authentication mechanism to function."
                                + "\n@param " + argName + " an class which implements <code>" + nm + "</code>"
                                + "\n@return this")
                        .returning(cb.className())
                        .body(typeAssign -> {
                            typeAssign.assignField(fieldName)
                                    .ofThis()
                                    .toInvocation("checkType")
                                    .withArgument(argName)
                                    .withClassArgument(nm)
                                    .inScope();
                            typeAssign.returningThis();
                        });
            });
        }
    }

}
