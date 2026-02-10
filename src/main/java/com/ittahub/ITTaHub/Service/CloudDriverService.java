package com.ittahub.ITTaHub.Service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittahub.ITTaHub.Utility.FileStructuring;
import com.ittahub.ITTaHub.Utility.Md5Hasher;
import com.ittahub.ITTaHub.Utility.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CloudDriverService {

    @Autowired
    private FileStructuring structor;

    @Autowired
    private Md5Hasher md5;

    public ResponseEntity<String> createNewRepository(String email, String container_id, String name, String desc, BlobServiceClient blobServiceClient) {

        email = new String(Base64.getDecoder().decode(email), StandardCharsets.UTF_8);
        container_id = new String(Base64.getDecoder().decode(container_id), StandardCharsets.UTF_8);
        name = new String(Base64.getDecoder().decode(name), StandardCharsets.UTF_8);
        desc = new String(Base64.getDecoder().decode(desc), StandardCharsets.UTF_8);
        BlobContainerClient container = blobServiceClient.getBlobContainerClient(container_id);

//        Any duplicates ?
        for (BlobItem blobItem : container.listBlobs())
            if (blobItem.getName().split("/")[0].equals(name))
                return ResponseEntity.status(HttpStatus.CONFLICT).body("File with give name " + name + " already exists");

        try {
            String fileContent = (desc.isEmpty() ? "" : desc) + "\nFile has been created by " + email + " at " + System.currentTimeMillis();
            BlobClient readmeBlob = container.getBlobClient(name + "/INIT_README.txt");
            readmeBlob.upload(new ByteArrayInputStream(fileContent.getBytes()), fileContent.length(), true);
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

    public ResponseEntity<LinkedHashMap<String, RepoDetails>> repositories(String id, BlobServiceClient blobServiceClient) {

        BlobContainerClient container = blobServiceClient.getBlobContainerClient(id);
        LinkedHashMap<String, Set<String>> extensions = new LinkedHashMap<>();
        LinkedHashMap<String, RepoDetails> repo = new LinkedHashMap<>();
        for (BlobItem blob : container.listBlobs()) {
            String rootFolder = blob.getName();
            String forFile = rootFolder.substring(0, rootFolder.indexOf("/"));
            if (!extensions.containsKey(forFile)) extensions.put(forFile, new LinkedHashSet<>());
            else extensions.get(forFile).add(rootFolder.substring(rootFolder.lastIndexOf(".") + 1));
        }

        for (BlobItem blob : container.listBlobs()) {
            String rootFolder = blob.getName();
            if (rootFolder.endsWith("/INIT_README.txt")) {
                BlobClient readmeBlob = container.getBlobClient(rootFolder);
                String readMeContent = readmeBlob.downloadContent().toString();
                String file = rootFolder.substring(0, rootFolder.indexOf("/"));
                repo.put(file, new RepoDetails(file, readMeContent, extensions.get(file), id));
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(repo);
    }

    public ResponseEntity<String> viewUserRepo(String root, BlobServiceClient blobServiceClient, String id) throws JsonProcessingException {

        try {

            root = new String(Base64.getDecoder().decode(root.getBytes()), StandardCharsets.UTF_8);

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);

            boolean fileTree = false;
            for (BlobItem blob : containerClient.listBlobs()) {
                if (blob.getName().startsWith(root)) {
                    structor.buildTree(blob.getName());
                    fileTree = true;
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            if (fileTree) {

                String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(structor.treeToJson(structor.root));
                structor.root = null;
                return ResponseEntity.status(HttpStatus.OK).body(jsonString);
            } else
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Root file has been deleted");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Please refresh");
        }
    }

    public ResponseEntity<String> sharedRepoView(String root, String repo, BlobServiceClient blobServiceClient) throws JsonProcessingException {
        root = new String(Base64.getDecoder().decode(root.getBytes()), StandardCharsets.UTF_8);
        repo = new String(Base64.getDecoder().decode(repo.getBytes()), StandardCharsets.UTF_8);

        try {

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(root);

            for (BlobItem blob : containerClient.listBlobs()) {
                if (blob.getName().startsWith(repo)) {
                    structor.buildTree(blob.getName());
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(structor.treeToJson(structor.root));
            structor.root = null;
            return ResponseEntity.status(HttpStatus.OK).body(jsonString);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(e.getMessage());
        }
    }

    public ResponseEntity<String> uploadFileToStorageService(String id, String relativePath, MultipartFile payload, BlobServiceClient blobServiceClient) {

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);
        BlobClient blob = containerClient.getBlobClient(relativePath);
        try {
            blob.upload(payload.getInputStream(), payload.getSize(), true);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Try again!!");
        }
        return ResponseEntity.status(HttpStatus.OK).body("Success");
    }

    public ResponseEntity<String> uploadFilesToStorageService(String id, List<MultipartFile> files, List<String> paths, BlobServiceClient blobServiceClient) {

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);

        int i = 0;
        int n = files.size();
        while (i < n) {
            BlobClient blob = containerClient.getBlobClient(paths.get(i));
            try {
                blob.upload(files.get(i).getInputStream(), files.get(i).getSize(), true);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Try again!!");
            }
            i++;
        }

        return ResponseEntity.status(HttpStatus.OK).body("Success");
    }

    public ResponseEntity<StreamingResponseBody> viewFileContentService(String id, String file, BlobServiceClient blobServiceClient) {
        try {

            System.out.println(id + " " + file);
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);
            BlobClient blob = containerClient.getBlobClient(file);

            if (!blob.exists())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);

            StreamingResponseBody stream = new StreamingResponseBody() {
                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    outputStream.write(blob.downloadContent().toBytes());
                }
            };
            System.out.println(stream.toString());
            return ResponseEntity.status(HttpStatus.OK).header("Content-Disposition", "attachment; filename=\"" + file + "\"").body(stream);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

    }

    public ResponseEntity<String> deleteBlobService(String id, String path, BlobServiceClient blobServiceClient) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);

        try {
            for (BlobItem blobItem : containerClient.listBlobs()) {
                if (blobItem.getName().startsWith(path)) {
                    BlobClient blob = containerClient.getBlobClient(blobItem.getName());
                    blob.delete();
                }
            }
            return ResponseEntity.status(HttpStatus.OK).body("Success");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retry again");
        }
    }

    public ResponseEntity<String> updateBlobService(String id, String path, MultipartFile content, BlobServiceClient blobServiceClient) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(id);
        BlobClient blob = containerClient.getBlobClient(path);
        try {

            blob.upload(content.getInputStream(), content.getSize(), true);
            return ResponseEntity.status(HttpStatus.OK).body("Success");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Try again");
        }
    }

    public ResponseEntity<StreamingResponseBody> searchBlobService(String search, BlobServiceClient blobServiceClient, Validator validate) {
        LinkedList<String[]> response = new LinkedList<>();
        if (validate.emailAddress(search)) {
            String containerId = md5.hash(search);
            try {
                BlobContainerClient container = blobServiceClient.getBlobContainerClient(containerId);
                if (!container.exists()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);

                for (BlobItem blob : container.listBlobs()) {
                    String folder = blob.getName();
                    if (!folder.startsWith(containerId) && folder.endsWith("/INIT_README.txt")) {
                        BlobClient readmeBlob = container.getBlobClient(folder);
                        String readMeContent = readmeBlob.downloadContent().toString();
                        String file = folder.substring(0, folder.indexOf("/"));
                        response.add(new String[]{containerId, file, readMeContent});
                    }
                }
                StreamingResponseBody stream = new StreamingResponseBody() {
                    @Override
                    public void writeTo(OutputStream outputStream) throws IOException {
                        String json = new ObjectMapper().writeValueAsString(response);
                        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                };
                return ResponseEntity.status(HttpStatus.OK).body(stream);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

        } else {
            System.out.println("Search by file");
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            LinkedHashSet<String[]> data = new LinkedHashSet<>();

            try {
                for (BlobContainerItem bci : blobServiceClient.listBlobContainers()) {
                    BlobContainerClient container = blobServiceClient.getBlobContainerClient(bci.getName());
                    for (BlobItem blob : container.listBlobs()) {
                        String containerName = bci.getName();
                        String blobName = blob.getName();
                        String blobPath = containerName + "/" + blobName;
                        if (blobPath.contains(search)) {

                            String sub_path = blobName.substring(0, blobName.indexOf("/"));
                            String path = containerName + "/" + sub_path;
                            if (!paths.contains(path)) {
                                String readMe = sub_path + "/INIT_README.txt";
                                BlobClient _blob_ = container.getBlobClient(readMe);
                                String content = new String(_blob_.downloadContent().toBytes(), StandardCharsets.UTF_8);
                                data.add(new String[]{containerName, sub_path, content});
                                paths.add(path);
                            }

                        }

                    }
                }
                StreamingResponseBody stream = new StreamingResponseBody() {
                    @Override
                    public void writeTo(OutputStream outputStream) throws IOException {
                        String json = new ObjectMapper().writeValueAsString(data);
                        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                };
//                data.clear();
//                paths.clear();
                return ResponseEntity.status(HttpStatus.OK).body(stream);

            } catch (Exception e) {
                System.out.println(e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

        }
    }

}
