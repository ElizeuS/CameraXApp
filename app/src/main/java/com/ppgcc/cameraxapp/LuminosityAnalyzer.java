package com.ppgcc.cameraxapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.ppgcc.cameraxapp.model.Pixel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class LuminosityAnalyzer implements ImageAnalysis.Analyzer {
    private long lastAnalyzedTimestamp = 0L;
    private final int INTERVAL = 300;
    Pixel[][] pixels;
    int[][] matDry;
    int[][] matCurrentIndices;

    double[] dryCellIndices;
    double[] wetCellIndices;
    double[] sprIndices;
    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private byte[] byteBufferToByteArray(ByteBuffer buffer) {
        buffer.rewind();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    private Bitmap byteBufferToBitmap(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        return bitmap;
    }

    private int[][] arrayToMatrix(byte[] data, int w, int h) {
        int[][] matrix = new int[h][w];
        int position = 0;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                matrix[i][j] = data[position] & 0xFF;
                //Log.d("INDEX", i+" "+j+" " + matrix[i][j]);
                position++;
            }
        }
        // Log.d("Posi", "" + bitmap[10][10]);
        return matrix;
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        long currentTimestamp = System.currentTimeMillis();
        // Calculate the average luma no more often than every second
        if (currentTimestamp - lastAnalyzedTimestamp >= /*TimeUnit.SECONDS.toMillis(1)*/ INTERVAL) {
            int w = image.getWidth();
            int h = image.getHeight();

            /*
             Since format in ImageAnalysis is YUV, image.planes[0]
             contains the Y (luminance) plane getPlanes()[0].getBuffer()
             contains the U (Cb)(chrominance BLUE) plane getPlanes()[1].getBuffer()
             contains the V (Cv)(chrominance RED) plane getPlanes()[2].getBuffer()
            */
            ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
            ByteBuffer bufferU = image.getPlanes()[1].getBuffer();
            ByteBuffer bufferV = image.getPlanes()[2].getBuffer();

            byte[] dataY = new byte[bufferY.remaining()];
            bufferY.get(dataY);
            byte[] dataU = new byte[bufferU.remaining()];
            bufferU.get(dataU);
            byte[] dataV = new byte[bufferV.remaining()];
            bufferV.get(dataV);

            //byte[] data = byteBufferToByteArray(bufferY);
            int matY[][] = arrayToMatrix(dataY, w, h);
            matCurrentIndices = matY;

            //Verifica e executa na primeira execução do programa
            verifyFisrtsInteration();

            //Transforma as matrizes nos vetores, calculando a média das linhas de ambos
            wetCellIndices = avgMatrixLine(matCurrentIndices, w, h);
            dryCellIndices = avgMatrixLine(matDry, w, h);

            sprIndices = sprCalc(dryCellIndices, wetCellIndices);

            // Update timestamp of last analyzed frame
            lastAnalyzedTimestamp = currentTimestamp;
        }
    }

    private Pixel[][] toRGB(int mY[][], int mU[][], int mV[][], int w, int h) {
        Pixel pixels[][] = new Pixel[h][w];
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                pixels[i][j].setRed((int) (mY[i][j] + 1.140 * mV[i][j]));
                pixels[i][j].setGreen((int) (mY[i][j] - 0.395 * mU[i][j] - 0.581 * mV[i][j]));
                pixels[i][j].setBlue((int) (mY[i][j] + 2.032 * mU[i][j]));
            }
        }

        return pixels;
    }


    public void luminosity(byte[] data) {
        // Extract image data from callback object
        // Convert the data into an array of pixel values
        // NOTE: this is translated from the following kotlin code, ain't sure about it being right
        // val pixels = data.map { it.toInt() and 0xFF }
        int[] pixels = new int[data.length];
        int pos = 0;
        for (byte b : data) {
            pixels[pos] = b & 0xFF;
            pos++;
        }
        // Compute average luminance for the image
        double luma = Arrays.stream(pixels).average().orElse(Double.NaN);
        // Log the new luma value
        Log.d("CameraXApp", "Average luminosity: " + pixels[10]);
    }

    // TODO ATRIBUIR A IMAGEM DRY A IMAGEM ATUAL
    public void dry() {
        this.matDry = this.matCurrentIndices;
    }

    private void verifyFisrtsInteration() {
        if (lastAnalyzedTimestamp == 0L) {
            this.matDry = this.matCurrentIndices;
        }
    }

    private double[] avgMatrixLine(int[][] mat, int w, int h) {
        int[] soma = new int[h];
        double[] media = new double[h];
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                if (soma[i] == 0) {
                    soma[i] = mat[i][j];
                } else {
                    //_soma[j] = _soma[j] + image.getIntComponent0(i, j);
                    soma[i] = soma[i] + mat[i][j];
                }
            }
        }
        for(int l = 0; l < soma.length; l++){
            media[l] = soma[l]/h;
           // Log.d("MEDIA", ""+media[l]);
        }

        return media;
    }

    public double[] sprCalc(double[] dry, double[] wet){
        double spr[] = new double[dry.length];
        for(int len = 0; len < wet.length; len++){
            spr[len] = wet[len]/dry[len];
        }
        return spr;
    }
}