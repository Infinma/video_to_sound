package application;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	
	private Mat image;
	
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	private String fileName;
	private SourceDataLine currentAudio;
	private boolean pause = false;
	
	@FXML
	private Slider slider;
	
	@FXML
	private Button pButton;
	
	@FXML
	private void initialize() {
		pButton.setDisable(true);
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
	}
	
	@FXML
	protected void selectPlayOrPause(ActionEvent event) {
		if (currentAudio != null) {
			if (pause) {
				currentAudio.start();
				pause = false;
				pButton.setText("Pause");
			} else {
				currentAudio.stop();
				pause = true;
				pButton.setText("Play");
			}
		}
	}
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		FileChooser fc = new FileChooser();
		fc.setInitialDirectory(new java.io.File("."));
		fc.setTitle("Open Video");
		File file = fc.showOpenDialog(null);
		return file.getAbsolutePath();
	}
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		pButton.setDisable(true);
		pause = true;
		pButton.setText("Play");
		try {
			fileName = getImageFilename();
		} catch (Exception e) {
			System.out.println(e);
			fileName = null;
		}
		if (fileName != null) {
			closeThreads();
			capture = new VideoCapture(fileName);
			if (capture.isOpened()) {
				pButton.setDisable(false);
				pButton.setText("Pause");
				pause = false;
				createFrameGrabber();
			}
		} else if (currentAudio != null) {
			pButton.setDisable(false);
		}
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}

	protected void playImage() throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		if (image != null) {
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            currentAudio = sourceDataLine;
            sourceDataLine.open(audioFormat, sampleRate);
            sourceDataLine.start();
            
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
            	}
            	while (pause) {
					try {
						Thread.sleep(100);
					} catch (Exception e) {
						System.out.println(e);
						closeThreads();
						break;
					}
				}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.drain();
            sourceDataLine.close();
		} else {
			// What should you do here?
		}
	}
	
	protected void createFrameGrabber() throws InterruptedException {
		if (capture != null && capture.isOpened()) {
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			
			Runnable frameGrabber = new Runnable() {
				@Override
				public void run() {
					Mat frame = new Mat();
					while (pause) {
						try {
							Thread.sleep(100);
						} catch (Exception e) {
							System.out.println(e);
							closeThreads();
						}
					}
					if (capture.read(frame)) {
						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(imageView.imageProperty(), im);
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						slider.setValue(currentFrameNumber/totalFrameCount*(slider.getMax() - slider.getMin()));
						try {
							image = frame;
							playImage();
						} catch (Exception e) {
							System.out.println(e);
						}
					} else {
						capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
					}
				}
			};
			if (timer != null && timer.isShutdown()) {
				timer.shutdown();
				timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			}
			
			timer = Executors.newSingleThreadScheduledExecutor();
			timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		}
	}
	
	protected void closeThreads() {
		if (currentAudio != null) {
			currentAudio.stop();
			currentAudio.close();
		}
		if (timer != null) {
			timer.shutdownNow();
		}
		
	}
}
