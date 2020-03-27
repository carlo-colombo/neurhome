package io.github.carlocolombo.neurhome;

public class App {
    private String packageName;
    private double value;

    public App(String packageName, double value) {
        this.packageName = packageName;
        this.value = value;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "App{" +
                "packageName='" + packageName + '\'' +
                ", value=" + value +
                '}';
    }
}
