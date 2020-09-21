package se.kth.castor.pankti.extract.launchers;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import se.kth.castor.pankti.extract.logging.CustomLogger;
import se.kth.castor.pankti.extract.processors.CandidateTagger;
import se.kth.castor.pankti.extract.processors.MethodProcessor;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class PanktiLauncher {
    private static final Logger LOGGER = CustomLogger.log(PanktiLauncher.class.getName());
    private static String projectName;

    public MavenLauncher getMavenLauncher(final String projectPath, final String projectName) {
        PanktiLauncher.projectName = projectName;
        MavenLauncher launcher = new MavenLauncher(projectPath, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        return launcher;
    }

    public CtModel buildSpoonModel(final MavenLauncher launcher) {
        launcher.buildModel();
        return launcher.getModel();
    }

    public int countMethods(final CtModel model) {
        int numberOfMethodsInProject = 0;
        for (CtType<?> s : model.getAllTypes()) numberOfMethodsInProject += s.getMethods().size();
        return numberOfMethodsInProject;
    }

    public void addMetaDataToCandidateMethods(Set<CtMethod<?>> candidateMethods) {
        for (CtMethod<?> candidateMethod : candidateMethods) {
            candidateMethod.putMetadata("pure", true);
        }
    }

    public void createCSVFile(Map<CtMethod<?>, Map<String, Boolean>> allMethodTags) throws IOException {
        String[] HEADERS = {"visibility", "parent-FQN", "method-name", "param-list", "return-type", "tags"};
        List<String> paramList;
        try (FileWriter out = new FileWriter("./extracted-methods-" + projectName +".csv");
             CSVPrinter csvPrinter = new CSVPrinter(out, CSVFormat.DEFAULT
                     .withHeader(HEADERS));
        ) {
            for (Map.Entry<CtMethod<?>, Map<String, Boolean>> entry : allMethodTags.entrySet()) {
                CtMethod<?> method = entry.getKey();
                paramList = new ArrayList<>();
                if (method.getParameters().size() > 0) {
                    for (CtParameter<?> parameter : method.getParameters()) {
                        paramList.add(parameter.getType().getQualifiedName());
                    }
                }
                Map<String, Boolean> tags = entry.getValue();
                csvPrinter.printRecord(
                        method.getVisibility(),
                        method.getParent(CtClass.class).getQualifiedName(),
                        method.getSimpleName(),
                        paramList,
                        method.getType().getQualifiedName(),
                        tags);
            }
        }
    }

    public Set<CtMethod<?>> applyProcessor(final CtModel model) {
        // Filter out pure methods and add metadata to them
        MethodProcessor methodProcessor = new MethodProcessor();
        model.processWith(methodProcessor);
        LOGGER.info(methodProcessor.toString());
        Set<CtMethod<?>> candidateMethods = methodProcessor.getCandidateMethods();
        addMetaDataToCandidateMethods(candidateMethods);

        // Tag pure methods based on their properties
        CandidateTagger candidateTagger = new CandidateTagger();
        model.processWith(candidateTagger);
        LOGGER.info(candidateTagger.toString());

        Map<CtMethod<?>, Map<String, Boolean>> allMethodTags = candidateTagger.getAllMethodTags();
        try {
            createCSVFile(allMethodTags);
        } catch (IOException e) {
            LOGGER.warning(e.getMessage());
        }
        LOGGER.info("Output saved in ./extracted-methods-" + projectName + ".csv");
        return candidateMethods;
    }
}
