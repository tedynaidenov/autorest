package com.intendia.gwt.autorest.processor;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.NATIVE;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.google.common.base.Throwables;
import com.intendia.gwt.autorest.client.AutoRestGwt;
import com.intendia.gwt.autorest.client.ResourceVisitor;
import com.intendia.gwt.autorest.client.RestServiceModel;
import com.intendia.gwt.autorest.client.TypeToken;
import com.intendia.gwt.autorest.processor.AnnotationProcessor.ParamInfo;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class AutoRestGwtProcessor extends AbstractProcessor {
    private static final Set<String> HTTP_METHODS = Stream.of(GET, POST, PUT, DELETE, HEAD, OPTIONS).collect(toSet());
    private static final String AutoRestGwt = AutoRestGwt.class.getCanonicalName();
    private Types typeUtils;
    private Elements elementUtils;
    
    @Override public Set<String> getSupportedOptions() { return singleton("debug"); }

    @Override public Set<String> getSupportedAnnotationTypes() { return singleton(AutoRestGwt); }

    @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

    @Override public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        
    }
    
    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        roundEnv.getElementsAnnotatedWith(AutoRestGwt.class).stream()
                .filter(e -> e.getKind().isInterface() && e instanceof TypeElement).map(e -> (TypeElement) e)
                .forEach(restService -> {
                    try {
                        processRestService(restService);
                    } catch (Exception e) {
                        // We don't allow exceptions of any kind to propagate to the compiler
                        error("uncaught exception processing rest service " + restService + ": " + e + "\n"
                                + Throwables.getStackTraceAsString(e));
                    }
                });
        return true;
    }

    private void processRestService(TypeElement restService) throws Exception {
    	AnnotatedElement restServiceAnnotatedElement = new AnnotatedElement(restService.getSimpleName().toString(), restService, restService.asType());
		
		AnnotationProcessor restServiceProcessor = new AnnotationProcessor(restServiceAnnotatedElement);
    	
        String[] rsPath = restServiceProcessor.getPaths(Stream.empty()).toArray(String[]::new);
        String[] produces = restServiceProcessor.getProduces().toArray(String[]::new);
        String[] consumes = restServiceProcessor.getConsumes().toArray(String[]::new);

        ClassName rsName = ClassName.get(restService);
        log("rest service interface: " + rsName);

        ClassName modelName = ClassName.get(rsName.packageName(), rsName.simpleName() + "_RestServiceModel");
        log("rest service model: " + modelName);

        TypeSpec.Builder modelTypeBuilder = TypeSpec.classBuilder(modelName.simpleName())
        		.addJavadoc(CodeBlock.of("This is generated class, please don't modify\n"))
        		.addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "\"" + getClass().getCanonicalName() + "\"")
                        .build())
                .addOriginatingElement(restService)
                .addModifiers(Modifier.PUBLIC)
                .superclass(RestServiceModel.class)
                .addSuperinterface(TypeName.get(restService.asType()));
                
        modelTypeBuilder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(Inject.class)
                .addModifiers(PUBLIC)
                .addParameter(TypeName.get(ResourceVisitor.Supplier.class), "parent", FINAL)
                .addStatement("super(new $T() { public $T get() { return $L.get().path($S); } })",
                        ResourceVisitor.Supplier.class, ResourceVisitor.class, "parent", rsPath.length > 0? rsPath[0]: "")
                .build());

        Map<ExecutableElement, ExecutableType> methods = getAllMethods(restService);
        
        Set<String> methodImports = new HashSet<>();
        for (Map.Entry<ExecutableElement, ExecutableType> method: methods.entrySet()) {
        	
        	AnnotatedElement annotatedElement = new AnnotatedElement(method.getKey().getSimpleName().toString(), method.getKey(), method.getValue());
    		AnnotationProcessor processor = new AnnotationProcessor(annotatedElement);
    		
            Optional<? extends AnnotationMirror> incompatible = isIncompatible(method.getKey());
            if (incompatible.isPresent()) {
                modelTypeBuilder.addMethod(MethodSpec.overriding(method.getKey())
                        .addAnnotation(AnnotationSpec.get(incompatible.get()))
                        .addStatement("throw new $T(\"$L\")", UnsupportedOperationException.class, annotatedElement.getSimpleName())
                        .build());
                continue;
            }

            CodeBlock.Builder builder = CodeBlock.builder().add("$[return ");
            {
            	List<? extends VariableElement> parameters = method.getKey().getParameters();
            	List<? extends TypeMirror> parameterTypes = method.getValue().getParameterTypes();

        		Supplier<Stream<? extends Entry<Integer, AnnotatedElement>>> parametersFactory = () -> 
	    			IntStream
	    				.range(0, parameters.size())
	    				.mapToObj(index -> new SimpleEntry<>(
	    					index, 
	    					new AnnotatedElement(parameters.get(index).getSimpleName().toString(), parameters.get(index), parameterTypes.get(index))));
    		
                // method type
                builder.add("method($L)", methodImport(methodImports, processor.getHttpMethod()));
                
                // resolve paths
                addObjectOrParamLiterals(builder, "path", processor.getPaths(parametersFactory.get()));

                // produces
                addStringLiterals(builder, "produces", processor.getProduces(produces));

                // consumes
                addStringLiterals(builder, "consumes", processor.getConsumes(consumes));
                
                // query params
                addParamLiterals(builder, "param", processor.getQueryParams(parametersFactory.get()));
                
                // header params
                addParamLiterals(builder, "header", processor.getHeaderParams(parametersFactory.get()));
                
                // form params
                addParamLiterals(builder, "form", processor.getFormParams(parametersFactory.get()));
                
                // data
                processor.getData(parametersFactory.get())
                	.ifPresent(dataInfo -> builder.add(
                		".data($L, $L)", 
                		dataInfo.getJavaArgumentName(), 
                		asTypeTokenLiteral(dataInfo.getAnnotatedElement())));
            }
            
            builder.add(".as($L);\n$]", asTypeTokenLiteral(annotatedElement));

            modelTypeBuilder.addMethod(MethodSpec.overriding(method.getKey(), (DeclaredType)restService.asType(), typeUtils).addCode(builder.build()).build());
        }

        Filer filer = processingEnv.getFiler();
        JavaFile.Builder file = JavaFile.builder(rsName.packageName(), modelTypeBuilder.build());
        for (String methodImport : methodImports) file.addStaticImport(HttpMethod.class, methodImport);
        boolean skipJavaLangImports = processingEnv.getOptions().containsKey("skipJavaLangImports");
        file.skipJavaLangImports(skipJavaLangImports).build().writeTo(filer);
    }
    
    private  Map<ExecutableElement, ExecutableType> getAllMethods(TypeElement restService) {
    	return 
    		elementUtils.getAllMembers(restService).stream()
                .filter(e -> e.getKind() == ElementKind.METHOD && e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(method -> !(
                	method.getModifiers().contains(STATIC)
                	|| method.getModifiers().contains(FINAL)
                	|| method.getModifiers().contains(NATIVE)
                	|| method.isDefault()))
                .filter(method -> (
                	isIncompatible(method).isPresent()	
                	|| method.getAnnotation(GET.class) != null
                	|| method.getAnnotation(PUT.class) != null
                	|| method.getAnnotation(POST.class) != null
                	|| method.getAnnotation(DELETE.class) != null))
                .collect(Collectors.toMap(m -> m, m -> ((ExecutableType)typeUtils.asMemberOf((DeclaredType)restService.asType() , m))));
    }
    
	private CodeBlock asTypeTokenLiteral(AnnotatedElement annotatedElement) {
		CodeBlock.Builder builder = CodeBlock.builder();
		
		AnnotatedElement jlmAnnotatedElement = (AnnotatedElement)annotatedElement;
		
		if (jlmAnnotatedElement.getJlmType() instanceof ExecutableType)
			addTypeTokenLiteral(builder, TypeName.get(((ExecutableType)jlmAnnotatedElement.getJlmType()).getReturnType()));
		else
			addTypeTokenLiteral(builder, TypeName.get(jlmAnnotatedElement.getJlmType()));
		
		return builder.build();
	}

    private void addTypeTokenLiteral(CodeBlock.Builder builder, TypeName name) {
    	builder.add("new $T<$L>(", TypeToken.class, name.isPrimitive()? name.box(): name);

    	TypeName rawType;
    	List<TypeName> typeArguments;
    	
    	if (name instanceof ParameterizedTypeName) {
    		ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)name;
    		rawType = parameterizedTypeName.rawType;
    		typeArguments = parameterizedTypeName.typeArguments;
    	} else if (name instanceof ArrayTypeName) {
    		ArrayTypeName arrayTypeName = (ArrayTypeName)name;
    		
    		rawType = null;
    		typeArguments = Collections.singletonList(arrayTypeName.componentType);
    	} else if (name instanceof ClassName || name instanceof TypeName) {
    		rawType = name.isPrimitive()? name.box(): name;
    		typeArguments = Collections.emptyList();
    	} else
    		throw new IllegalArgumentException("Unsupported type " + name); 
    	
    	if(rawType == null)
    		builder.add("null");
    	else
    		builder.add("$T.class", rawType);
    	
    	for (TypeName typeArgumentName: typeArguments) {
    		builder.add(", ");
    		addTypeTokenLiteral(builder, typeArgumentName);
    	}
    	
    	builder.add(") {}");
    }

    private void addParamLiterals(CodeBlock.Builder builder, String name, Stream<? extends ParamInfo> params) {
        params
    		.forEach(paramInfo -> builder.add(
				".$L($S, $L, $L)",
				name,
				paramInfo.getName(), 
				paramInfo.getJavaArgumentName(), 
				asTypeTokenLiteral(paramInfo.getAnnotatedElement())));
    }
    
    private void addObjectOrParamLiterals(CodeBlock.Builder builder, String name, Stream<?> items) {
        items
    		.forEach(item -> {
    			if (item instanceof ParamInfo) {
    				ParamInfo paramInfo = (ParamInfo)item;
    				builder.add(
    					".$L($L, $L)",
    					name,
    					paramInfo.getJavaArgumentName(),
    					asTypeTokenLiteral(paramInfo.getAnnotatedElement()));
    			} else {
    				builder.add(".$L($S)", name, item.toString());
    			}
    		});
        
    }
    
    private static void addStringLiterals(CodeBlock.Builder builder, String name, Stream<String> strings) {
        builder.add(".$L(", name);
        addCommaSeparated(builder, strings, (cb, path) -> cb.add("$S", path));
        builder.add(")");
    }
    
    private static <T> void addCommaSeparated(CodeBlock.Builder builder, Stream<? extends T> items, BiConsumer<CodeBlock.Builder, T> consumer) {
    	boolean[] first = {true};
    	
		items.forEach(item -> {
			if (!first[0])
				builder.add("$L", ", ");
			
			consumer.accept(builder, item);
			
			first[0] = false;
		});
    }
    
    private String methodImport(Set<String> methodImports, String method) {
        if (HTTP_METHODS.contains(method)) {
            methodImports.add(method); return method;
        } else {
            return "\"" + method + "\"";
        }
    }

    public boolean isParam(VariableElement a) {
        return a.getAnnotation(CookieParam.class) != null
                || a.getAnnotation(FormParam.class) != null
                || a.getAnnotation(HeaderParam.class) != null
                || a.getAnnotation(MatrixParam.class) != null
                || a.getAnnotation(PathParam.class) != null
                || a.getAnnotation(QueryParam.class) != null;
    }

    private Optional<? extends AnnotationMirror> isIncompatible(ExecutableElement method) {
        return method.getAnnotationMirrors().stream().filter(this::isIncompatible).findAny();
    }

    private boolean isIncompatible(AnnotationMirror a) {
        return a.getAnnotationType().toString().endsWith("GwtIncompatible");
    }

    private void log(String msg) {
        if (processingEnv.getOptions().containsKey("debug")) {
            processingEnv.getMessager().printMessage(Kind.NOTE, msg);
        }
    }

    private void error(String msg) {
        processingEnv.getMessager().printMessage(Kind.ERROR, msg);
    }
}
