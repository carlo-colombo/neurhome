package io.github.carlocolombo.neurhome;

import org.encog.ConsoleStatusReportable;
import org.encog.ml.MLFactory;
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
import java.util.Arrays;
import java.util.List;

public class Starter {
    public static void main(String[] args) {

        System.out.println("asdasdasdasd");

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


        System.out.println("training error: " + EncogUtility.calculateRegressionError(bestMethod, em.getTrainingDataset()));
        System.out.println("validation error:" + EncogUtility.calculateRegressionError(bestMethod, em.getValidationDataset()));
        NormalizationHelper helper = versatileMLDataSet.getNormHelper();
        System.out.println(helper.toString());
        System.out.println("final: " + bestMethod);


        MLData input = helper.allocateInputVector();

        List<String> inputsToCheck = Arrays.asList(
                "com.amazon.mShop.android.shopping,2,21,720",
                "taxi.android.client,0,4,494"
        );

        for (String row : inputsToCheck) {
            System.out.println(row);
            int index = 0;
            for (String col : row.split(",")) {
                double[] n = helper.normalizeInputColumn(index++, col);
                for (double i : n) {
                    System.out.println(i);
                }
                System.out.println("-----------------");
            }
        }
    }
}
