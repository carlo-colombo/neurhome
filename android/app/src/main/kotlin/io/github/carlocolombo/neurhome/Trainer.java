package io.github.carlocolombo.neurhome;

import org.encog.ConsoleStatusReportable;
import org.encog.ml.MLRegression;
import org.encog.ml.data.MLData;
import org.encog.ml.data.versatile.NormalizationHelper;
import org.encog.ml.data.versatile.VersatileMLDataSet;
import org.encog.ml.data.versatile.columns.ColumnDefinition;
import org.encog.ml.data.versatile.columns.ColumnType;
import org.encog.ml.data.versatile.sources.CSVDataSource;
import org.encog.ml.data.versatile.sources.VersatileDataSource;
import org.encog.ml.factory.MLMethodFactory;
import org.encog.ml.model.EncogModel;
import org.encog.util.csv.CSVFormat;
import org.encog.util.simple.EncogUtility;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

class Trainer {
    public static void doStuff(PrintStream out) {
        out.println("==============================");
        out.println("the new start");
        out.println("==============================");


        VersatileDataSource vds = new CSVDataSource(new File("/tmp/db.csv"), true, CSVFormat.DECIMAL_POINT);

        VersatileMLDataSet versatileMLDataSet = new VersatileMLDataSet(vds);

        List<ColumnDefinition> inputs = Arrays.asList(
                versatileMLDataSet.defineSourceColumn("package", ColumnType.nominal),
                versatileMLDataSet.defineSourceColumn("wifiID", ColumnType.nominal),
                versatileMLDataSet.defineSourceColumn("geohash_7ID", ColumnType.nominal),
                versatileMLDataSet.defineSourceColumn("minutes2", ColumnType.continuous));

        inputs.forEach(versatileMLDataSet::defineInput);

        ColumnDefinition output = versatileMLDataSet.defineSourceColumn("output", ColumnType.nominal);

        versatileMLDataSet.defineOutput(output);

        versatileMLDataSet.analyze();

        EncogModel em = new EncogModel(versatileMLDataSet);

        em.selectMethod(versatileMLDataSet, MLMethodFactory.TYPE_FEEDFORWARD);
        em.setReport(new ConsoleStatusReportable());
        versatileMLDataSet.normalize();

        em.holdBackValidation(0.3, true, 1001);
        em.selectTrainingType(versatileMLDataSet);
        MLRegression bestMethod = (MLRegression) em.crossvalidate(5, true);


        out.println("training error: " + EncogUtility.calculateRegressionError(bestMethod, em.getTrainingDataset()));
        out.println("validation error:" + EncogUtility.calculateRegressionError(bestMethod, em.getValidationDataset()));
        NormalizationHelper helper = versatileMLDataSet.getNormHelper();
        out.println(helper.toString());
        out.println("final: " + bestMethod);

        try {
            List<App> apps = new ArrayList<>();
            Scanner scanner = new Scanner(new File("/tmp/apps.txt"));
            while (scanner.hasNextLine()) {
                String packageName = scanner.nextLine();
                double val = checkInput(packageName + ",2,21,720", bestMethod, helper);
                apps.add(new App(packageName, val));
            }

            apps.sort((o1, o2) -> Double.compare(o1.getValue(), o2.getValue()));

            apps.forEach(out::println);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static double checkInput(String row, MLRegression bestMethod, NormalizationHelper helper) {
        MLData input = helper.allocateInputVector();
        System.out.println(row);
        helper.normalizeInputVector(row.split(","), input.getData(), true);

        MLData outputMLDATA = bestMethod.compute(input);

        System.out.println(outputMLDATA);
        for (double i : outputMLDATA.getData()) {
            System.out.println(i);
        }
        System.out.println("------end-----------");
        return outputMLDATA.getData(0);
    }
}
