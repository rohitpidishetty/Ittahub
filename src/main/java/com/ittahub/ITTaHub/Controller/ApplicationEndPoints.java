package com.ittahub.ITTaHub.Controller;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.ittahub.ITTaHub.Service.AuthServices;
import com.ittahub.ITTaHub.Service.CloudDriverService;
import com.ittahub.ITTaHub.Utility.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@CrossOrigin
@RequestMapping("/auth-service")
public class ApplicationEndPoints {
    @Autowired
    private Validator validator;

    private Firestore fsClient;

    private BlobServiceClient blobServiceClient;

    public ApplicationEndPoints(@Value("${SDK}") String SDK, @Value("${CONNECTION}") String connectionString, @Value("${STORAGE}") String storageName) throws Exception {

        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        try (InputStream serviceAccount = new ByteArrayInputStream(Objects.requireNonNull(SDK).getBytes(StandardCharsets.UTF_8));) {

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId("ittahub")
                    .build();

            if (FirebaseApp.getApps().isEmpty())
                FirebaseApp.initializeApp(options);

            this.fsClient = FirestoreClient.getFirestore();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Autowired
    private AuthServices authServices;

    @Autowired
    private CloudDriverService cloudDriverService;

    @GetMapping("/login")
    public ResponseEntity<String> login(@RequestParam("x1") String email_id, @RequestParam("x2") String password) throws Exception {
        return authServices.loginService(email_id, password, this.fsClient);
    }

    @GetMapping("/signup")
    public ResponseEntity<String> signUp(@RequestParam("x1") String email_id, @RequestParam("x2") String password, @RequestParam("x3") String phone, @RequestParam("x4") String firstname, @RequestParam("x5") String lastname) throws Exception {
        return authServices.signUp(email_id, password, phone, firstname, lastname, validator, fsClient, blobServiceClient);
    }

    @GetMapping("/is-authorized")
    public ResponseEntity<Map<String, Object>> isAuthorized(@RequestParam("x1") String email_id, @RequestParam("x2") String password) throws Exception {
        return authServices.isAuthorized(email_id, password, this.fsClient);
    }

    @GetMapping("/update")
    public ResponseEntity<String> updateDetails(@RequestParam("email_id") String email_id, @RequestParam("firstname") String firstname, @RequestParam("lastname") String lastname, @RequestParam("bio") String bio, @RequestParam("portfolio") String portfolio, @RequestParam("instagram") String instagram, @RequestParam("linkedin") String linkedin, @RequestParam("youtube") String youtube) throws Exception {
        return authServices.updateDetails(email_id, firstname, lastname, bio, portfolio, instagram, linkedin, youtube, validator, this.fsClient);
    }

    @GetMapping("/create-new-repo")
    public ResponseEntity<String> createNewRepo(@RequestParam("email") String email, @RequestParam("container_id") String container_id, @RequestParam("name") String name, @RequestParam("desc") String desc) throws Exception {
        return cloudDriverService.createNewRepository(email, container_id, name, desc, blobServiceClient);
    }

    @GetMapping("/user-repositories")
    public ResponseEntity<LinkedHashMap<String, CloudDriverService.RepoDetails>> populateUserRepos(@RequestParam("id") String id) {
        return cloudDriverService.repositories(id, blobServiceClient);
    }

    @GetMapping("/repository-content")
    public ResponseEntity<String> repoView(@RequestParam("id") String id, @RequestParam("root") String root) throws Exception {
        return cloudDriverService.viewUserRepo(root, blobServiceClient, id);
    }

    @PostMapping("/upload-file-to-storage")
    public ResponseEntity<String> uploadFileToStorage(@RequestParam("id") String id, @RequestParam("relativePath") String relativePath, @RequestParam("payload") MultipartFile payload) throws Exception {
        return cloudDriverService.uploadFileToStorageService(id, relativePath, payload, blobServiceClient);
    }

    @PostMapping("/upload-files-to-storage")
    public ResponseEntity<String> uploadFilesToStorage(@RequestParam("id") String id, @RequestParam("files") List<MultipartFile> files, @RequestParam("relativePath") List<String> relativePath) throws Exception {
        return cloudDriverService.uploadFilesToStorageService(id, files, relativePath, blobServiceClient);
    }

    @GetMapping("/file-viewer")
    public ResponseEntity<StreamingResponseBody> fileViewer(@RequestParam("id") String id, @RequestParam("file") String file) {
        return cloudDriverService.viewFileContentService(id, file, blobServiceClient);
    }

    @GetMapping("/delete-blob")
    public ResponseEntity<String> deleteBlob(@RequestParam("id") String id, @RequestParam("path") String path) throws Exception {
        return cloudDriverService.deleteBlobService(id, path, blobServiceClient);
    }

    @PostMapping("/update-blob")
    public ResponseEntity<String> updateBlob(@RequestParam("id") String id, @RequestParam("path") String path, @RequestParam("content") MultipartFile content) throws Exception {
        return cloudDriverService.updateBlobService(id, path, content, blobServiceClient);
    }

    @GetMapping("/search-repo")
    public ResponseEntity<StreamingResponseBody> searchBlob(@RequestParam("search") String search) throws Exception {
        return cloudDriverService.searchBlobService(search, blobServiceClient, validator);
    }

    @GetMapping("/shared-repository-content")
    public ResponseEntity<String> sharedRepoView(@RequestParam("root") String root, @RequestParam("repo") String repo) throws Exception {
        return cloudDriverService.sharedRepoView(root, repo, blobServiceClient);
    }

    @GetMapping("/shared-file-viewer")
    public ResponseEntity<StreamingResponseBody> sharedFileViewer(@RequestParam("root") String root, @RequestParam("file") String file) {
        return cloudDriverService.viewFileContentService(root, file, blobServiceClient);
    }

    @GetMapping("/get-user-details")
    public ResponseEntity<Map<String, Object>> getUserDetails(@RequestParam("email") String email) throws Exception {
        return authServices.getUserDetails(email, fsClient);
    }

    @GetMapping("/clone-repository")
    public ResponseEntity<String> cloneRepo(@RequestParam("clone_from_container") String clone_from_container, @RequestParam("clone_from_node") String clone_from_node, @RequestParam("clone_to_container") String clone_to_container, @RequestParam("clone_to_node") String clone_to_node, @RequestParam("user") String user) {
        return cloudDriverService.cloneRepoService(clone_from_container, clone_from_node, clone_to_container, clone_to_node, user, blobServiceClient);
    }

    @GetMapping("/download-repository")
    public ResponseEntity<StreamingResponseBody> downloadRepo(@RequestParam("clone_from_container") String clone_from_container, @RequestParam("clone_from_node") String clone_from_node) {
        return cloudDriverService.downloadRepoService(clone_from_container, clone_from_node, blobServiceClient);
    }
}
