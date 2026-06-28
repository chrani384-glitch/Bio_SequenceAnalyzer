package com.bioanalyzer.model;

import javafx.beans.property.*;

/**
 * AnalysisResult — holds one row in the results table.
 * Used for both the main analysis table and mutation table.
 */
public class AnalysisResult {

    private final StringProperty  property;
    private final StringProperty  value;
    private final StringProperty  description;
    private final StringProperty  category;

    public AnalysisResult(String property, String value,
                          String description, String category) {
        this.property    = new SimpleStringProperty(property);
        this.value       = new SimpleStringProperty(value);
        this.description = new SimpleStringProperty(description);
        this.category    = new SimpleStringProperty(category);
    }

    public StringProperty propertyProperty()    { return property; }
    public StringProperty valueProperty()       { return value; }
    public StringProperty descriptionProperty() { return description; }
    public StringProperty categoryProperty()    { return category; }

    public String getProperty()    { return property.get(); }
    public String getValue()       { return value.get(); }
    public String getDescription() { return description.get(); }
    public String getCategory()    { return category.get(); }
}
