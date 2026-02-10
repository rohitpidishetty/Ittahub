package com.ittahub.ITTaHub.Service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.ittahub.ITTaHub.Utility.Md5Hasher;
import com.ittahub.ITTaHub.Utility.Validator;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AuthServices {

    @Autowired
    private Md5Hasher hashFn;

    public ResponseEntity<String> loginService(String email_id, String password, Firestore fsClient) throws Exception {
//        http://localhost:8080/auth-service/login?x1=cm9oaXRwaWRpc2hldHR5QGdtYWlsLmNvbQ==&x2=MTIzNDU2Nw==
        email_id = new String(Base64.getDecoder().decode(email_id), StandardCharsets.UTF_8);
        password = new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
        DocumentReference docRef = fsClient.collection("Users").document(email_id);
        DocumentSnapshot docSnap = docRef.get().get();
        if (!docSnap.exists()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found");
        return Objects.equals(docSnap.get("password"), password) ? ResponseEntity.status(HttpStatus.OK).body("Success") : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Try again");
    }

    public ResponseEntity<Map<String, Object>> isAuthorized(String email_id, String password, Firestore fsClient) throws Exception {
        email_id = new String(Base64.getDecoder().decode(email_id), StandardCharsets.UTF_8);
        password = new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
        DocumentReference docRef = fsClient.collection("Users").document(email_id);
        DocumentSnapshot docSnap = docRef.get().get();
        if (!docSnap.exists()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        return Objects.equals(docSnap.get("password"), password) ? ResponseEntity.status(HttpStatus.OK).body(docSnap.getData()) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
    }

    public ResponseEntity<String> updateDetails(String email_id, String firstname, String lastname, String bio, String portfolio, String instagram, String linkedin, String youtube, Validator validator, Firestore fsClient) throws Exception {
//        http://localhost:8080/auth-service/signup?x1=rohitpidishetty@gmail.com&x2=12345678&x3=8106573008&x4=rohit%20viswakarma&x5=p
        email_id = new String(Base64.getDecoder().decode(email_id), StandardCharsets.UTF_8);
        firstname = new String(Base64.getDecoder().decode(firstname), StandardCharsets.UTF_8);
        lastname = new String(Base64.getDecoder().decode(lastname), StandardCharsets.UTF_8);
        bio = new String(Base64.getDecoder().decode(bio), StandardCharsets.UTF_8);
        portfolio = new String(Base64.getDecoder().decode(portfolio), StandardCharsets.UTF_8);
        instagram = new String(Base64.getDecoder().decode(instagram), StandardCharsets.UTF_8);
        linkedin = new String(Base64.getDecoder().decode(linkedin), StandardCharsets.UTF_8);
        youtube = new String(Base64.getDecoder().decode(youtube), StandardCharsets.UTF_8);

        if (!validator.firstName(firstname) || !validator.lastName(lastname))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Name");

        DocumentReference docRef = fsClient.collection("Users").document(email_id);
        DocumentSnapshot docSnap = docRef.get().get();

        if (!docSnap.exists()) return ResponseEntity.status(HttpStatus.CONFLICT).body("User Not Found");

        try {
            docRef.update(
                    "firstname", firstname,
                    "lastname", lastname,
                    "bio", bio,
                    "hyperlinks.portfolio", portfolio,
                    "hyperlinks.instagram", instagram,
                    "hyperlinks.linkedin", linkedin,
                    "hyperlinks.youtube", youtube
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("Unable to update details at this moment, please try again later");
        }
        return ResponseEntity.status(200).body("Success");
    }

    public ResponseEntity<String> signUp(String email_id, String password, String phone, String firstname, String lastname, Validator validator, Firestore fsClient, BlobServiceClient blobServiceClient) throws Exception {
//        http://localhost:8080/auth-service/signup?x1=rohitpidishetty@gmail.com&x2=12345678&x3=8106573008&x4=rohit%20viswakarma&x5=p
        email_id = new String(Base64.getDecoder().decode(email_id), StandardCharsets.UTF_8);
        password = new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
        phone = new String(Base64.getDecoder().decode(phone), StandardCharsets.UTF_8);
        firstname = new String(Base64.getDecoder().decode(firstname), StandardCharsets.UTF_8);
        lastname = new String(Base64.getDecoder().decode(lastname), StandardCharsets.UTF_8);
        System.out.println(email_id + " " + password + " " + phone + " " + firstname + " " + lastname);

        if (!validator.firstName(firstname) || !validator.lastName(lastname))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Name");
        if (!validator.phoneNumber(phone))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Phone Number");
        if (!validator.emailAddress(email_id))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Email Address");
        if (password.length() < 3)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Weak Password");

        DocumentReference docRef = fsClient.collection("Users").document(email_id);
        DocumentSnapshot docSnap = docRef.get().get();

        if (docSnap.exists()) return ResponseEntity.status(HttpStatus.CONFLICT).body("User already exists");

        System.out.println("New user");

        Map<String, String> hyperlinks = new HashMap<>();
        hyperlinks.put("portfolio", null);
        hyperlinks.put("instagram", null);
        hyperlinks.put("linkedin", null);
        hyperlinks.put("youtube", null);

        String uniqueId = hashFn.hash(email_id);
        Map<String, Object> data = new HashMap<>();
        data.put("id", uniqueId);
        data.put("emailId", email_id);
        data.put("password", password);
        data.put("phone", phone);
        data.put("firstname", firstname);
        data.put("lastname", lastname);
        data.put("bio", "Enter bio");
        data.put("hyperlinks", hyperlinks);

        try {
            docRef.set(data).get();
            // Create a blob container for this specific user
            BlobContainerClient clientsContainer =
                    blobServiceClient.getBlobContainerClient(uniqueId);
            clientsContainer.createIfNotExists();

            String fileContent = "Hello, world !, this file has been created by " + email_id + " at " + System.currentTimeMillis();
            BlobClient readmeBlob = clientsContainer.getBlobClient(uniqueId + "/INIT_README.txt");
            readmeBlob.upload(new ByteArrayInputStream(fileContent.getBytes()), fileContent.length(), true);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("User not saved");
        }

        return ResponseEntity.status(200).body("Success");
    }

    public ResponseEntity<Map<String, Object>> getUserDetails(String email, Firestore fsClient) throws Exception {
        email = new String(Base64.getDecoder().decode(email), StandardCharsets.UTF_8);
        DocumentReference docRef = fsClient.collection("Users").document(email);
        DocumentSnapshot docSnap = docRef.get().get();
        if (!docSnap.exists())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        Map<String, Object> data = docSnap.getData();
        assert data != null;
        data.remove("password");

        return ResponseEntity.status(HttpStatus.OK).body(data);
    }
}

