package se.martinuhlen.exiftimetravel;

import static java.util.Arrays.sort;
import static java.util.Comparator.comparing;
import static org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED;
import static org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

public class ExifTimeTravel
{
	// https://stackoverflow.com/questions/36868013/editing-jpeg-exif-data-with-java

	private static final DateTimeFormatter EXIF_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

	public static void main(String[] args) throws Exception
	{
		final File sourceDir = new File("/media/data/Photos/Fiske/Temp");
		final int hourDiff = 1;
		final int minuteDiff = 0;
		final String outputFormat = "yyyyMMdd_HHmmss";
		final DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);

		File[] sourceFiles = sourceDir.listFiles();
		sort(sourceFiles, comparing(File::getName));

		for (File sourceFile : sourceFiles)
		{
			JpegImageMetadata metadata = (JpegImageMetadata) Imaging.getMetadata(sourceFile);
			TiffImageMetadata exif = metadata.getExif();
			TiffOutputSet outputSet = exif.getOutputSet();
			String timeString = exif.getFieldValue(EXIF_TAG_DATE_TIME_ORIGINAL)[0];
			LocalDateTime oldTime = LocalDateTime.parse(timeString, EXIF_TIME_FORMAT);
			LocalDateTime newTime = oldTime.plusHours(hourDiff).plusMinutes(minuteDiff);

			//System.out.println(metadata);

			TiffOutputDirectory exifDirectory = outputSet.getExifDirectory();
			exifDirectory.removeField(EXIF_TAG_DATE_TIME_ORIGINAL);
			exifDirectory.removeField(EXIF_TAG_DATE_TIME_DIGITIZED);
			exifDirectory.add(EXIF_TAG_DATE_TIME_ORIGINAL, newTime.format(EXIF_TIME_FORMAT));
			exifDirectory.add(EXIF_TAG_DATE_TIME_DIGITIZED, newTime.format(EXIF_TIME_FORMAT));

			TiffOutputDirectory rootDirectory = outputSet.getRootDirectory();
			TiffOutputField rootDateTimeField = rootDirectory.getFields().stream().filter(f -> f.tagInfo.name.equals("DateTime")).findAny().get();
			rootDirectory.removeField(rootDateTimeField.tagInfo);
			rootDirectory.add((TagInfoAscii) rootDateTimeField.tagInfo, newTime.format(EXIF_TIME_FORMAT));

			outputSet.getDirectories().stream().filter(dir -> dir.description().equals("Sub")).findAny().ifPresent(subDirectory ->
			{
				subDirectory.getFields().stream().filter(f -> f.tagInfo.name.equals("DateTime")).findAny().ifPresent(subDateTimeField ->
				{
					subDirectory.removeField(subDateTimeField.tagInfo);
					try
					{
						subDirectory.add((TagInfoAscii) subDateTimeField.tagInfo, newTime.format(EXIF_TIME_FORMAT));
					}
					catch (ImageWriteException e)
					{
						throw new RuntimeException(e);
					}
				});
			});

			String newFileName = newTime.format(outputFormatter) + ".jpg";
			File newFile = new File(sourceFile.getParent(), newFileName);
			String tempFileName = newFileName + ".temp";
			File tempFile = new File(sourceFile.getParent(), tempFileName);

			System.out.println("Changing " + sourceFile.getName() + " with time '" + oldTime + "' to " + newFileName + " with time '" + newTime + "'");

			try(BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile)))
			{
				new ExifRewriter().updateExifMetadataLossless(sourceFile, os, outputSet);
			}

			for (int i = 0; i < 50 && sourceFile.exists(); i++)
			{
				sourceFile.delete();
				Thread.sleep(100);
			}
			if (sourceFile.exists())
			{
				throw new IllegalStateException("Cannot delete '" + sourceFile + "', all files were not processed!");
			}

			for (int i = 0; i < 50 && tempFile.exists(); i++)
			{
				tempFile.renameTo(newFile);
				Thread.sleep(100);
			}
			if (tempFile.exists())
			{
				System.err.println("Failed to remove temp suffix from '" + tempFile + "'");
			}
		}
	}
}
