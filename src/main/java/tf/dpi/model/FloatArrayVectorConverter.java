package tf.dpi.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class FloatArrayVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] vector) {
        if (vector == null) {
            return null;
        }

        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }

    @Override
    public float[] convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String contents = value.substring(1, value.length() - 1);
        if (contents.isBlank()) {
            return new float[0];
        }

        String[] parts = contents.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
