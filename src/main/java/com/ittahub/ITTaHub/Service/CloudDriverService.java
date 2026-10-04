package com.ittahub.ITTaHub.Service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.database.core.Repo;
import com.ittahub.ITTaHub.Utility.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class CloudDriverService {

  @Autowired
  private RootFilePathBuilder rootFilePathBuilder;

  @Autowired
  private Md5Hasher md5;

  @Autowired
  private EmailUser email;

  @Autowired
  private S3Client s3Client;


  public ResponseEntity<String> createNewRepository(String email, String container_id, String name, String desc) {
    email = new String(Base64.getDecoder().decode(email), StandardCharsets.UTF_8);
    container_id = new String(Base64.getDecoder().decode(container_id), StandardCharsets.UTF_8);
    name = new String(Base64.getDecoder().decode(name), StandardCharsets.UTF_8);
    desc = new String(Base64.getDecoder().decode(desc), StandardCharsets.UTF_8);
    desc = desc.concat(String.format("\nCreated by %s, at %d", email, System.currentTimeMillis()));


    if (!s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket("ittahub").prefix(container_id + "/" + name + "/").maxKeys(1).build()).contents().isEmpty())
      return ResponseEntity.status(HttpStatus.CONFLICT).body("File with give name " + name + " already exists");

    String key = container_id + "/" + name + "/INIT_README.txt";

    try {
      PutObjectRequest request = PutObjectRequest.builder().bucket("ittahub").key(key).contentType("text/plain").build();

      s3Client.putObject(request, RequestBody.fromString(desc));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("Could not process request at this time, please try again later");
    }
    return ResponseEntity.status(HttpStatus.OK).body("Success");
  }

  public static class RepoDetails {

    protected String repoName;
    protected String root;
    protected String repoDescription;
    protected Set<String> files;

    public RepoDetails(String repoName,
                       String repoDescription,
                       String root,
                       HashSet<String> set) {

      this.files = new HashSet<>();
      this.repoDescription = repoDescription;
      this.repoName = repoName;
      this.root = root;
    }

    public RepoDetails(String repoName, String repoDescription, Set<String> files, String root) {
      this.repoName = repoName;
      this.repoDescription = repoDescription;
      this.files = files;
      this.root = root;
    }

    public String getRepoName() {
      return this.repoName;
    }

    public String getRepoDescription() {
      return this.repoDescription;
    }

    public String getRoot() {
      return this.root;
    }

    public Set<String> getFiles() {
      return this.files;
    }
  }

  @Value("${aws.s3.bucket}")
  private String bucket;

  public ResponseEntity<LinkedHashMap<String, RepoDetails>> repositories(String id) {

    System.out.println("Repos for id " + id);
    LinkedHashMap<String, RepoDetails> repo = new LinkedHashMap<>();

    try {
      ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(id + "/").build();
      List<S3Object> res = s3Client.listObjectsV2(request).contents();
      if (res.isEmpty()) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);

      for (S3Object obj : res) {
        String path = obj.key();
        String[] pathTokens = path.split("/");
        if (pathTokens[pathTokens.length - 1].equals("INIT_README.txt")) {
          GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(path).build();
          repo.put(
              pathTokens[1],
              new RepoDetails(pathTokens[pathTokens.length - 2],
                  s3Client.getObjectAsBytes(getRequest).asUtf8String(),
                  pathTokens[0],
                  new HashSet<String>())
          );
        } else {
          if (pathTokens.length > 1)
            repo.get(pathTokens[1]).files.add(pathTokens[pathTokens.length - 1].contains(".") ? (pathTokens[pathTokens.length - 1].split("[.]")[1]) : "");
        }
      }

    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }

    System.out.println(repo);

    return ResponseEntity.status(HttpStatus.OK).body(repo);
  }


  public ResponseEntity<String> viewUserRepo(String root, String id)
      throws JsonProcessingException {

    try {

      root = new String(
          Base64.getDecoder().decode(root.getBytes()),
          StandardCharsets.UTF_8
      );

      String prefix = id + "/" + root;

      ListObjectsV2Request request = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(prefix)
          .build();

      ListObjectsV2Response response = s3Client.listObjectsV2(request);

      List<String> hasPaths = new ArrayList<>();

      for (S3Object object : response.contents())
        hasPaths.add(object.key());


      boolean fileTree = !hasPaths.isEmpty();

      String[] relativePaths = new String[hasPaths.size()];

      int idx = 0;


      for (String path : hasPaths)
        relativePaths[idx++] = path.substring(id.length() + 1);


      ObjectMapper mapper = new ObjectMapper();

      if (fileTree) {

        String jsonString = mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(
                rootFilePathBuilder
                    .addPaths(relativePaths)
                    .buildTree()
                    .normalize()
            );

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(jsonString);

      } else {

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Root file has been deleted");
      }

    } catch (Exception e) {


      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Please refresh");
    }
  }


  public ResponseEntity<StreamingResponseBody> viewFileContentService(
      String id,
      String file
  ) {
    try {

      String key = id + "/" + file;

      HeadObjectRequest headRequest = HeadObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .build();

      try {
        s3Client.headObject(headRequest);
      } catch (NoSuchKeyException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(null);
      }

      StreamingResponseBody stream = outputStream -> {

        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        try (ResponseInputStream<GetObjectResponse> inputStream =
                 s3Client.getObject(getRequest)) {

          inputStream.transferTo(outputStream);
        }
      };

      return ResponseEntity
          .status(HttpStatus.OK)
          .header(
              "Content-Disposition",
              "attachment; filename=\"" +
                  file.substring(file.lastIndexOf("/") + 1) +
                  "\""
          )
          .body(stream);

    } catch (Exception e) {

      System.out.println(e.getMessage());

      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(null);
    }
  }


  public ResponseEntity<String> uploadFileToStorageService(
      String id,
      String relativePath,
      MultipartFile payload
  ) {
    try {

      String key = id + "/" + relativePath;

      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .contentType(payload.getContentType())
          .build();

      s3Client.putObject(
          request,
          RequestBody.fromInputStream(
              payload.getInputStream(),
              payload.getSize()
          )
      );

      return ResponseEntity
          .status(HttpStatus.OK)
          .body("Success");

    } catch (IOException e) {

      System.out.println(e.getMessage());

      return ResponseEntity
          .status(HttpStatus.CONFLICT)
          .body("Try again!!");

    } catch (Exception e) {

      System.out.println(e.getMessage());

      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Try again!!");
    }
  }

  public ResponseEntity<String> uploadFilesToStorageService(
      String id,
      List<MultipartFile> files,
      List<String> paths
  ) {

    int i = 0;
    int n = files.size();

    while (i < n) {

      try {

        String key = id + "/" + paths.get(i);

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(files.get(i).getContentType())
            .build();

        s3Client.putObject(
            request,
            RequestBody.fromInputStream(
                files.get(i).getInputStream(),
                files.get(i).getSize()
            )
        );

      } catch (IOException e) {

        System.out.println(e.getMessage());

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body("Try again!!");

      } catch (Exception e) {

        System.out.println(e.getMessage());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Try again!!");
      }

      i++;
    }

    return ResponseEntity
        .status(HttpStatus.OK)
        .body("Success");
  }


  public ResponseEntity<String> deleteBlobService(
      String id,
      String path
  ) {

    try {


      String prefix = id + "/" + path;

      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(prefix)
          .build();

      ListObjectsV2Response response =
          s3Client.listObjectsV2(listRequest);

      for (S3Object object : response.contents()) {

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(object.key())
            .build();

        s3Client.deleteObject(deleteRequest);
      }

      return ResponseEntity
          .status(HttpStatus.OK)
          .body("Success");

    } catch (Exception e) {

      System.out.println(e.getMessage());

      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Retry again");
    }
  }

  public ResponseEntity<String> updateBlobService(
      String id,
      String path,
      MultipartFile content
  ) {

    try {

      String key = id + "/" + path;

      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .contentType(content.getContentType())
          .build();

      s3Client.putObject(
          request,
          RequestBody.fromInputStream(
              content.getInputStream(),
              content.getSize()
          )
      );

      return ResponseEntity
          .status(HttpStatus.OK)
          .body("Success");

    } catch (IOException e) {


      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Try again");
    }
  }

  public ResponseEntity<StreamingResponseBody> searchBlobService(
      String search,
      Validator validate
  ) {

    LinkedList<String[]> response = new LinkedList<>();

    if (validate.emailAddress(search)) {

      String containerId = md5.hash(search);

      try {

        String prefix = containerId + "/";

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .build();

        ListObjectsV2Response listResponse =
            s3Client.listObjectsV2(listRequest);

        if (listResponse.contents().isEmpty()) {
          return ResponseEntity
              .status(HttpStatus.NOT_FOUND)
              .body(null);
        }

        for (S3Object object : listResponse.contents()) {

          String key = object.key();

          if (key.endsWith("/INIT_README.txt")) {

            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            String readMeContent = s3Client
                .getObjectAsBytes(getRequest)
                .asUtf8String();

            String relativePath = key.substring(prefix.length());

            String file = relativePath.substring(
                0,
                relativePath.indexOf("/")
            );

            response.add(
                new String[]{
                    containerId,
                    file,
                    readMeContent
                }
            );
          }
        }

        StreamingResponseBody stream = outputStream -> {

          String json = new ObjectMapper()
              .writeValueAsString(response);

          outputStream.write(
              json.getBytes(StandardCharsets.UTF_8)
          );
        };

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(stream);

      } catch (Exception e) {

        System.out.println(e.getMessage());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(null);
      }

    } else {

      System.out.println("Search by file");
      System.out.println(search);

      LinkedHashSet<String> paths = new LinkedHashSet<>();
      LinkedList<String[]> data = new LinkedList<>();

      try {

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucket)
            .build();

        ListObjectsV2Response listResponse =
            s3Client.listObjectsV2(listRequest);

        for (S3Object object : listResponse.contents()) {

          String key = object.key();

          if (!key.toLowerCase().contains(search.toLowerCase()))
            continue;

          String[] tokens = key.split("/");

          if (tokens.length < 2)
            continue;

          String containerId = tokens[0];
          String repoName = tokens[1];

          String repoPath = containerId + "/" + repoName;

          if (paths.contains(repoPath))
            continue;

          String readmeKey =
              containerId +
                  "/" +
                  repoName +
                  "/INIT_README.txt";

          try {

            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(readmeKey)
                .build();

            String content = s3Client
                .getObjectAsBytes(getRequest)
                .asUtf8String();

            data.add(
                new String[]{
                    containerId,
                    repoName,
                    content
                }
            );

            paths.add(repoPath);

          } catch (Exception ignored) {
          }
        }

        StreamingResponseBody stream = outputStream -> {

          String json = new ObjectMapper()
              .writeValueAsString(data);

          outputStream.write(
              json.getBytes(StandardCharsets.UTF_8)
          );
        };

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(stream);

      } catch (Exception e) {

        System.out.println(e.getMessage());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(null);
      }
    }
  }


  public ResponseEntity<StreamingResponseBody> downloadRepoService(
      String clone_from_container,
      String clone_from_node
  ) {

    final String _clone_from_container = new String(
        Base64.getDecoder().decode(clone_from_container.getBytes()),
        StandardCharsets.UTF_8
    );

    final String _clone_from_node = new String(
        Base64.getDecoder().decode(clone_from_node.getBytes()),
        StandardCharsets.UTF_8
    );

    String prefix =
        _clone_from_container + "/" + _clone_from_node + "/";

    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(prefix)
        .build();

    ListObjectsV2Response listResponse =
        s3Client.listObjectsV2(listRequest);

    if (listResponse.contents().isEmpty()) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .body(null);
    }

    StreamingResponseBody stream = outputStream -> {

      try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {

        for (S3Object object : listResponse.contents()) {

          String key = object.key();

          if (key.endsWith("/"))
            continue;

          GetObjectRequest getRequest = GetObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .build();

          String relativePath = key.substring(
              (_clone_from_container + "/").length()
          );

          zos.putNextEntry(
              new ZipEntry(relativePath)
          );

          try (ResponseInputStream<GetObjectResponse> inputStream =
                   s3Client.getObject(getRequest)) {

            byte[] buffer = new byte[8192];

            int read;

            while ((read = inputStream.read(buffer)) != -1) {
              zos.write(buffer, 0, read);
            }
          }

          zos.closeEntry();
        }

        zos.finish();
      }
    };

    return ResponseEntity
        .ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + _clone_from_node + ".zip\""
        )
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(stream);
  }

  public ResponseEntity<String> sharedRepoView(
      String root,
      String repo
  ) throws JsonProcessingException {

    root = new String(
        Base64.getDecoder().decode(root.getBytes()),
        StandardCharsets.UTF_8
    );

    repo = new String(
        Base64.getDecoder().decode(repo.getBytes()),
        StandardCharsets.UTF_8
    );

    try {

      String prefix = root + "/" + repo + "/";

      ListObjectsV2Request request = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(prefix)
          .build();

      ListObjectsV2Response response =
          s3Client.listObjectsV2(request);

      List<String> hasPaths = new ArrayList<>();

      for (S3Object object : response.contents()) {

        String key = object.key();

        String relativePath = key.substring(
            (root + "/").length()
        );

        hasPaths.add(relativePath);
      }

      String[] relativePaths =
          hasPaths.toArray(new String[0]);

      ObjectMapper mapper = new ObjectMapper();

      if (!hasPaths.isEmpty()) {

        String jsonString = mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(
                rootFilePathBuilder
                    .addPaths(relativePaths)
                    .buildTree()
                    .normalize()
            );

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(jsonString);
      }

      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .body("File not present");

    } catch (Exception e) {

      System.out.println(e.getMessage());

      return ResponseEntity
          .status(HttpStatus.NO_CONTENT)
          .body(e.getMessage());
    }
  }

  public ResponseEntity<String> cloneRepoService(
      String clone_from_container,
      String clone_from_node,
      String clone_to_container,
      String clone_to_node,
      String user
  ) {

    final String _clone_from_container = new String(
        Base64.getDecoder().decode(clone_from_container.getBytes()),
        StandardCharsets.UTF_8
    );

    final String _clone_from_node = new String(
        Base64.getDecoder().decode(clone_from_node.getBytes()),
        StandardCharsets.UTF_8
    );

    final String _clone_to_container = new String(
        Base64.getDecoder().decode(clone_to_container.getBytes()),
        StandardCharsets.UTF_8
    );

    final String _clone_to_node = new String(
        Base64.getDecoder().decode(clone_to_node.getBytes()),
        StandardCharsets.UTF_8
    );

    final String _user = new String(
        Base64.getDecoder().decode(user.getBytes()),
        StandardCharsets.UTF_8
    );

    CompletableFuture.runAsync(() -> {

      try {

        String sourcePrefix =
            _clone_from_container + "/" + _clone_from_node + "/";

        String targetPrefix =
            _clone_to_container + "/" + _clone_to_node + "/";

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(sourcePrefix)
            .build();

        ListObjectsV2Response response =
            s3Client.listObjectsV2(listRequest);

        for (S3Object object : response.contents()) {

          String sourceKey = object.key();

          if (sourceKey.endsWith("/"))
            continue;

          String relativePath = sourceKey.substring(
              sourcePrefix.length()
          );

          String targetKey =
              targetPrefix + relativePath;

          CopyObjectRequest copyRequest = CopyObjectRequest.builder()
              .sourceBucket(bucket)
              .sourceKey(sourceKey)
              .destinationBucket(bucket)
              .destinationKey(targetKey)
              .build();

          s3Client.copyObject(copyRequest);
        }

        String initContent =
            "File has been cloned by node " +
                _clone_to_container +
                ", from node " +
                _clone_from_container +
                " at " +
                System.currentTimeMillis();

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(targetPrefix + "INIT_README.txt")
            .contentType("text/plain")
            .build();

        s3Client.putObject(
            putRequest,
            RequestBody.fromString(initContent)
        );

        email.sendSimpleEmail(
            _user,
            "ITTaHub-Repo cloning status",
            _clone_from_node,
            _clone_to_node
        );

      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
    });

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(
            "Cloning in progress. You will receive a notification when the operation is completed"
        );
  }
}
