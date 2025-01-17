package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.weaving.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodAspect0Nested0 {
    private static int INVOCATION_COUNT;
    private static boolean fileSizeWithinLimits = true;

    @Pointcut(className = "fully.qualified.path.to.class",
            methodName = "methodToInstrument",
            methodParameterTypes = {"param1", "param2"},
            timerName = "Timer - name")
    public static class TargetMethodAdvice implements AdviceTemplate {
        private static final TimerName timer = Agent.getTimerName(TargetMethodAdvice.class);
        private static final String transactionType = "Target";
        private static final double COUNT = 0;
        private static long profileSizePre;
        private static String paramObjectsFilePath;
        private static String returnedObjectFilePath;
        private static String invocationCountFilePath;
        private static String invokedMethodsCSVFilePath;
        private static String objectProfileSizeFilePath;
        private static String libraryInvocationFilePath;
        private static String parentInvocationClassName = MethodAspect0.TargetMethodAdvice.class.getAnnotation(Pointcut.class).className();
        private static String parentInvocationMethodName = MethodAspect0.TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodName();
        private static Logger logger = Logger.getLogger(TargetMethodAdvice.class);
        private static final String methodParamTypesString = String.join(",", TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodParameterTypes());
        private static final String postfix = methodParamTypesString.isEmpty() ? "" : "_" + methodParamTypesString;
        private static final String methodFQN = TargetMethodAdvice.class.getAnnotation(Pointcut.class).className() + "."
                + TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodName() + postfix;
        static UUID invocationUuid = null;
        static long invocationTimestamp;
        private static final boolean invocationOnLibraryMethod = false;
        private static final String invocationString = String.format("Invocation count for %s: ", methodFQN.replaceAll("\\[\\]", "%5b%5d"));
        private static File[] allObjectFiles;

        private static String sanitizeMethodFQN() {
            return methodFQN.replaceAll("\\[\\]", "%5b%5d")
                    .replaceAll("\\$", ".")
                    .replaceAll(",", "_");
        }

        private static synchronized void gatherDataForInvocationOfLibraryMethod() {
            setup();
            logger.info(String.format("Writing invocation %s to file %s",
                    INVOCATION_COUNT, libraryInvocationFilePath));
            INVOCATION_COUNT++;
            invocationTimestamp = Instant.now().toEpochMilli();
            try {
                FileWriter objectFileWriter = new FileWriter(libraryInvocationFilePath, true);
                String toWrite = String.format("<%s parent=\"%s.%s\" parent-uuid=\"%s\" timestamp=\"%s\"/>",
                        sanitizeMethodFQN(),
                        parentInvocationClassName, parentInvocationMethodName,
                        invocationUuid, invocationTimestamp);
                objectFileWriter.write(toWrite + "\n");
                objectFileWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static void setup() {
            AdviceTemplate.setUpXStream();
            Map<Type, String> fileNameMap = AdviceTemplate.setUpFiles("nested-" + methodFQN);
            paramObjectsFilePath = fileNameMap.get(Type.PARAMS);
            returnedObjectFilePath = fileNameMap.get(Type.RETURNED);
            invocationCountFilePath = fileNameMap.get(Type.INVOCATION_COUNT);
            invokedMethodsCSVFilePath = fileNameMap.get(Type.INVOKED_METHODS);
            objectProfileSizeFilePath = fileNameMap.get(Type.OBJECT_PROFILE_SIZE);
            allObjectFiles = new File[]{
                    new File(returnedObjectFilePath),
                    new File(paramObjectsFilePath)};
            checkFileSizeLimit();
        }

        public static long getObjectProfileSize() {
            long objectProfileSize = 0L;
            for (File file : allObjectFiles) {
                objectProfileSize += file.length();
            }
            return objectProfileSize;
        }

        // Limit object XML files to ~200 MB
        public static void checkFileSizeLimit() {
            for (File file : allObjectFiles) {
                if (file.exists() && (file.length() / (1024 * 1024) >= 200)) {
                    fileSizeWithinLimits = false;
                    break;
                }
            }
        }

        public static String handleNonPrimitiveParamSerialization(Object objectToWrite) {
            List<String> primitives = Arrays.asList("boolean", "byte", "char", "double", "float",
                    "int", "long", "short", "java.lang.String");
            String[] paramTypes = TargetMethodAdvice.class.getAnnotation(Pointcut.class).methodParameterTypes();

            if (paramTypes.length == 0 ||
                    Arrays.stream(paramTypes).allMatch(primitives::contains)) {
                return xStream.toXML(objectToWrite);
            }

            Object[] paramObjectArray = (Object[]) objectToWrite;
            StringBuilder serializedParams = new StringBuilder();
            serializedParams.append("<object-array>\n");
            for (int i = 0; i < paramTypes.length; i++) {
                if (!primitives.contains(paramTypes[i])) {
                    paramTypes[i] = paramTypes[i].replaceAll("\\[]", "-array")
                            .replaceAll("\\$", ".");

                    serializedParams.append("  <")
                            .append(paramTypes[i])
                            .append("/>").append("\n");
                } else {
                    serializedParams.append("  ")
                            .append(xStream.toXML(paramObjectArray[i]))
                            .append("\n");
                }
            }
            serializedParams.append("</object-array>\n");
            return serializedParams.toString();
        }

        public static synchronized void writeObjectXMLToFile(boolean isParameter,
                                                             Object objectToWrite,
                                                             String objectFilePath) {
            try {
                FileWriter objectFileWriter = new FileWriter(objectFilePath, true);
                String xml;
                if (isParameter) {
                    // We don't want to serialize non-primitive parameters
                    xml = handleNonPrimitiveParamSerialization(objectToWrite);
                } else {
                    xml = xStream.toXML(objectToWrite);
                }
                xml = xml.replaceAll("(&#x)(\\w+;)", "&amp;#x$2");
                xml = xml.replaceFirst("(\\/*)>",
                        " parent-uuid=\"" + invocationUuid +
                                "\" timestamp=\"" + invocationTimestamp + "\"$1>");
                BufferedReader reader = new BufferedReader(new StringReader(xml));
                BufferedWriter writer = new BufferedWriter(objectFileWriter);
                while ((xml = reader.readLine()) != null) {
                    writer.write(xml);
                    writer.newLine();
                }
                writer.flush();
                writer.close();
            } catch (Exception e) {
                logger.info("Exception when writing XML for MethodAspect" + COUNT + " to file " + objectFilePath);
                e.printStackTrace();
                if (e.getMessage().startsWith("No converter specified") || e.getMessage().startsWith("No converter available")) {
                    String className = extractClassNameFromTheExceptionMessage(e.getMessage());
                    registerConverterAtRuntime(className);
                    logger.info("Automatically register a converter for: " + className);
                }
            }
        }

        // Write size (in bytes) of individual object profile to file
        public static synchronized void writeObjectProfileSizeToFile(long size) {
            try {
                FileWriter objectFileWriter = new FileWriter(objectProfileSizeFilePath, true);
                objectFileWriter.write(size + "\n");
                objectFileWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static synchronized void writeInvocationCountToFile() {
            try {
                File invocationCountFile = new File(invocationCountFilePath);

                if (!invocationCountFile.exists()) {
                    FileWriter objectFileWriter = new FileWriter(invocationCountFilePath);
                    objectFileWriter.write(invocationString + INVOCATION_COUNT);
                    objectFileWriter.close();
                } else {
                    String content = new String(Files.readAllBytes(Paths.get(invocationCountFilePath)));
                    Pattern pattern = Pattern.compile(invocationString + "(\\d+$)");
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        int count = Integer.parseInt(matcher.group(1)) + 1;
                        content = matcher.replaceAll(invocationString + count);
                        Files.write(invocationCountFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // For mocking: instrument and collect parameters and returned values if this invocation is nested
        @IsEnabled
        public synchronized static boolean isNestedInvocation() {
            invocationUuid = null;
            if (INVOCATION_COUNT >= 10)
                return false;
            boolean isNestedInvocation = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(s ->
                    s.getClassName().equals(parentInvocationClassName) &
                            s.getMethodName().equals(parentInvocationMethodName));
            if (isNestedInvocation) {
                if (!invocationOnLibraryMethod) {
                    logger.info("Aspect " + COUNT + " is a nested invocation");
                    invocationUuid = MethodAspect0.TargetMethodAdvice.invocationUuid;
                } else if (invocationOnLibraryMethod) {
                    logger.info("Aspect " + COUNT + " is a nested invocation on a library method");
                    if (Thread.currentThread().getName().equals(MethodAspect0.TargetMethodAdvice.methodFQN)) {
                        invocationUuid = MethodAspect0.TargetMethodAdvice.invocationUuid;
                    }
                }
            }
            return invocationUuid != null & isNestedInvocation;
        }

        @OnBefore
        public static TraceEntry onBefore(OptionalThreadContext context,
                                          @BindParameterArray Object parameterObjects,
                                          @BindMethodName String methodName) {
            setup();
            if (fileSizeWithinLimits) {
                invocationTimestamp = Instant.now().toEpochMilli();
                profileSizePre = getObjectProfileSize();
                writeObjectXMLToFile(true, parameterObjects, paramObjectsFilePath);
                INVOCATION_COUNT++;
            }
            MessageSupplier messageSupplier = MessageSupplier.create(
                    "className: {}, methodName: {}",
                    TargetMethodAdvice.class.getAnnotation(Pointcut.class).className(),
                    methodName
            );
            return context.startTransaction(transactionType, methodName, messageSupplier, timer, OptionalThreadContext.AlreadyInTransactionBehavior.CAPTURE_NEW_TRANSACTION);
        }

        @OnReturn
        public static void onReturn(@BindReturn Object returnedObject,
                                    @BindTraveler TraceEntry traceEntry) {
            if (fileSizeWithinLimits) {
                writeObjectXMLToFile(false, returnedObject, returnedObjectFilePath);
                writeObjectProfileSizeToFile(getObjectProfileSize() - profileSizePre);
                checkFileSizeLimit();
            }
            writeInvocationCountToFile();
            invocationUuid = null;
            traceEntry.end();
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable throwable,
                                   @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(throwable);
        }

        public static String extractClassNameFromTheExceptionMessage(String exceptionMessage) {
            String className = "";

            String pattern = "type\\s+:\\s+(\\S*)\\n";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(exceptionMessage);
            if (m.find()) {
                className = m.group(1);
            }

            return className;
        }

        public static void registerConverterAtRuntime(String className) {
            xStream.registerConverter(new Converter() {
                @Override
                public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
                }

                @Override
                public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
                    return null;
                }

                @Override
                public boolean canConvert(Class aClass) {
                    return aClass.getCanonicalName().equals(className);
                }
            });
        }
    }
}
