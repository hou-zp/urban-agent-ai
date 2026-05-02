package com.example.urbanagent.knowledge.application;

final class EmbeddingVectorCodec {

    private EmbeddingVectorCodec() {
    }

    static String serialize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder(vector.length * 8);
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.toString();
    }

    static float[] deserialize(String value) {
        if (value == null || value.isBlank()) {
            return new float[0];
        }
        String[] parts = value.split(",");
        float[] vector = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try {
                vector[index] = Float.parseFloat(parts[index].trim());
            } catch (NumberFormatException ex) {
                return new float[0];
            }
        }
        return vector;
    }

    static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
