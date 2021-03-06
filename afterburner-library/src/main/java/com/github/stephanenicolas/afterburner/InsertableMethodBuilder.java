package com.github.stephanenicolas.afterburner;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import com.github.stephanenicolas.afterburner.exception.AfterBurnerImpossibleException;
import com.github.stephanenicolas.afterburner.inserts.CtMethodJavaWriter;
import com.github.stephanenicolas.afterburner.inserts.InsertableMethod;
import com.github.stephanenicolas.afterburner.inserts.SimpleInsertableMethod;
import lombok.extern.slf4j.Slf4j;

/**
 * Almost a DSL/builder to ease creating an {@link InsertableMethod}. Needs more
 * intermediate states.
 * 
 * @author SNI
 */
@Slf4j
public class InsertableMethodBuilder {

    private String targetMethod;
    private CtClass classToInsertInto;
    protected String fullMethod;
    protected String body;
    protected String insertionBeforeMethod;
    protected String insertionAfterMethod;
    private AfterBurner afterBurner;
    private CtMethodJavaWriter signatureExtractor;

    public InsertableMethodBuilder(AfterBurner afterBurner) {
        this(afterBurner, null);
    }

    public InsertableMethodBuilder(AfterBurner afterBurner,
            CtMethodJavaWriter signatureExtractor) {
        this.afterBurner = afterBurner;
        this.signatureExtractor = signatureExtractor;
    }

    public StateTargetClassSet insertIntoClass(Class<?> clazzToInsertInto) throws NotFoundException {
        this.classToInsertInto = ClassPool.getDefault().get(
                clazzToInsertInto.getName());
        return new StateTargetClassSet();
    }

    public StateTargetClassSet insertIntoClass(CtClass classToInsertInto) {
        this.classToInsertInto = classToInsertInto;
        return new StateTargetClassSet();
    }

    private void doInsertBodyInFullMethod() {
        if (fullMethod != null) {
            if (!fullMethod.contains(InsertableMethod.BODY_TAG)) {
                log.info("Full method doesn't contain body tag (InsertableMethod.BODY_TAG=" + InsertableMethod.BODY_TAG + ")");
            }
            fullMethod = fullMethod.replace(InsertableMethod.BODY_TAG, body);
        }
    }

    protected void checkFields() throws AfterBurnerImpossibleException {
        boolean hasInsertionMethod = insertionBeforeMethod != null
                || insertionAfterMethod != null;
        if (classToInsertInto == null || targetMethod == null
                || !hasInsertionMethod || body == null || fullMethod == null) {
            throw new AfterBurnerImpossibleException(
                    "Builder was not used as intended. A field is null.");
        }
    }
    
    //**********************************************
    //******* FLUENT DSL STATE CLASSES
    //**********************************************


    public class StateTargetClassSet {
        public StateTargetMethodSet inMethodIfExists(String targetMethod) {
            InsertableMethodBuilder.this.targetMethod = targetMethod;
            return new StateTargetMethodSet();
        }

        public StateInsertionPointAndFullMethodSet beforeOverrideMethod(String targetMethod) throws NotFoundException {
            InsertableMethodBuilder.this.targetMethod = targetMethod;
            InsertableMethodBuilder.this.insertionBeforeMethod = targetMethod;
            CtMethod overridenMethod = findTargetMethod(targetMethod);
            if (overridenMethod == null) {
                throw new NotFoundException(String.format("Class %s doesn't contain any method named %s", classToInsertInto.getName(), targetMethod));
            }
            fullMethod = signatureExtractor
                    .createJavaSignature(overridenMethod)
                    + " { \n"
                    + InsertableMethod.BODY_TAG
                    + "\n"
                    + signatureExtractor.invokeSuper(overridenMethod) + "}\n";
            log.info("Creating override " + fullMethod);
            return new StateInsertionPointAndFullMethodSet();
        }

        public StateInsertionPointAndFullMethodSet afterOverrideMethod(String targetMethod) throws NotFoundException {
            InsertableMethodBuilder.this.targetMethod = targetMethod;
            InsertableMethodBuilder.this.insertionAfterMethod = targetMethod;
            CtMethod overridenMethod = findTargetMethod(targetMethod);
            if (overridenMethod == null) {
                throw new NotFoundException(String.format("Class %s doesn't contain any method named %s", classToInsertInto.getName(), targetMethod));
            }
            fullMethod = signatureExtractor
                    .createJavaSignature(overridenMethod)
                    + " { \n"
                    + signatureExtractor.invokeSuper(overridenMethod)
                    + "\n"
                    + InsertableMethod.BODY_TAG + "}\n";
            log.info("Creating override " + fullMethod);
            return new StateInsertionPointAndFullMethodSet();
        }

        private CtMethod findTargetMethod(String targetMethod) {
            CtMethod overridenMethod = null;
            try {
                overridenMethod = classToInsertInto
                        .getDeclaredMethod(targetMethod);
            } catch (Exception e) {
                for (CtMethod method : classToInsertInto.getMethods()) {
                    if (method.getName().equals(targetMethod)) {
                        overridenMethod = method;
                    }
                }
            }
            return overridenMethod;
        }
    }

    public class StateTargetMethodSet {
        public StateInsertionPointSet beforeACallTo(String insertionBeforeMethod) {
            InsertableMethodBuilder.this.insertionBeforeMethod = insertionBeforeMethod;
            return new StateInsertionPointSet();
        }

        public StateInsertionPointSet afterACallTo(String insertionAfterMethod) {
            InsertableMethodBuilder.this.insertionAfterMethod = insertionAfterMethod;
            return new StateInsertionPointSet();
        }
    }

    public class StateInsertionPointSet {
        public StateBodySet withBody(String body) {
            InsertableMethodBuilder.this.body = body;
            return new StateBodySet();
        }
    }

    public class StateInsertionPointAndFullMethodSet {
        public StateComplete withBody(String body) {
            InsertableMethodBuilder.this.body = body;
            return new StateComplete();
        }
    }

    public class StateBodySet {
        public StateComplete elseCreateMethodIfNotExists(String fullMethod) {
            InsertableMethodBuilder.this.fullMethod = fullMethod;
            return new StateComplete();
        }
    }

    public class StateComplete {

        public InsertableMethod createInsertableMethod() throws AfterBurnerImpossibleException {
            checkFields();
            doInsertBodyInFullMethod();

            InsertableMethod method = new SimpleInsertableMethod(
                    classToInsertInto, targetMethod, insertionBeforeMethod,
                    insertionAfterMethod, body, fullMethod);
            return method;
        }

        public void doIt() throws CannotCompileException,
                AfterBurnerImpossibleException {
            InsertableMethod method = createInsertableMethod();
            afterBurner.addOrInsertMethod(method);
        }
    }

}
