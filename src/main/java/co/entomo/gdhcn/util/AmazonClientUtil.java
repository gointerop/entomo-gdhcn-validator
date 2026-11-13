package co.entomo.gdhcn.util;

import java.io.File;
import java.io.InputStream;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.nio.charset.StandardCharsets;


import lombok.extern.slf4j.Slf4j;

/**
 *  @author Uday Matta
 *  @organization entomo Labs
 * Utility class for interacting with Amazon S3.
 * This class provides methods for uploading and retrieving files from an S3 bucket.
 */
@Slf4j
@Component
public class AmazonClientUtil {

	private S3Client s3client;
	
	@Value("${aws.bucket.s3.bucket.name}")
    private String bucketName;
    @Value("${aws.bucket.access.key}")
    private String accessKey;
    @Value("${aws.bucket.access.secret}")
    private String secretKey;

    /**
     * Initializes the Amazon S3 client with the provided credentials and region.
     * This method is automatically called after the bean's properties have been set.
     */
	@PostConstruct
    private void initializeAmazonClient() {
        AwsCredentialsProvider credentials = (accessKey == null || accessKey.isBlank())
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        this.s3client = S3Client.builder().credentialsProvider(credentials)
                .region(Region.AP_SOUTHEAST_1).build();
    }

    @PreDestroy
    void close() {
        if (s3client != null) {
            s3client.close();
        }
    }
    /**
     * Uploads a file as a string to the specified folder in the S3 bucket.
     *
     * @param fileName       the name of the file to be uploaded.
     * @param fileContent    the content of the file as a {@code String}.
     * @param folderToUpload the folder within the S3 bucket where the file will be uploaded.
     */
	public void uploadFileToBucket(String fileName, String fileContent, String folderToUpload) {
        log.info("Uploading file {} to {}", fileName, folderToUpload);
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName)
                .key(folderToUpload + "/" + fileName).contentType("application/json").build();
        s3client.putObject(request, RequestBody.fromString(fileContent, StandardCharsets.UTF_8));
    }
    /**
     * Uploads a file to the specified folder in the S3 bucket.
     *
     * @param fileName       the name of the file to be uploaded.
     * @param file           the {@code File} object to be uploaded.
     * @param folderToUpload the folder within the S3 bucket where the file will be uploaded.
     */
	public void uploadFileToBucket(String fileName, File file, String folderToUpload) {
        log.info("Uploading file {} to {}", fileName, folderToUpload);
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName)
                .key(folderToUpload + "/" + fileName).build();
        s3client.putObject(request, RequestBody.fromFile(file));
    }

    /**
     * Retrieves a file from the specified folder in the S3 bucket and returns it as a {@code File} object.
     *
     * @param filename   the name of the file to be retrieved.
     * @param folderName the folder within the S3 bucket where the file is stored.
     * @return a {@code File} object representing the retrieved file.
     */
	public File getFileFromBucket(String filename, String folderName) 
	{
        InputStream inputStream = getFileInputStream(filename, folderName);
        File file = new File(filename);
        return file;
    }
    /**
     * Retrieves a file from the specified folder in the S3 bucket and returns its content as an {@code InputStream}.
     *
     * @param filename   the name of the file to be retrieved.
     * @param folderName the folder within the S3 bucket where the file is stored.
     * @return an {@code InputStream} representing the content of the retrieved file.
     */
    public InputStream getFileInputStream(String filename, String folderName) {
        return s3client.getObject(GetObjectRequest.builder().bucket(bucketName)
                .key(folderName + "/" + filename).build());
    }
}
