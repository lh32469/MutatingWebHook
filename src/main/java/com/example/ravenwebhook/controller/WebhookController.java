package com.example.ravenwebhook.controller;

import com.example.ravenwebhook.service.PodMutationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.Status;
import io.kubernetes.client.openapi.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

  private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

  private final PodMutationService mutationService;
  private final ObjectMapper objectMapper;

  public WebhookController(PodMutationService mutationService,
                           ObjectMapper objectMapper) {
    this.mutationService = mutationService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/mutate")
  public ResponseEntity<AdmissionReview> mutate(@RequestBody String rawBody) {
    logger.info("Received raw request: {}", rawBody);

    AdmissionReview admissionReview = null;
    try {
      // First, try to parse the admission review
      admissionReview = objectMapper.readValue(rawBody, AdmissionReview.class);

      logger.info("Parsed AdmissionReview: apiVersion={}, kind={}, request={}",
                  admissionReview.getApiVersion(),
                  admissionReview.getKind(),
                  admissionReview.getRequest());

      if (admissionReview.getRequest() == null) {
        logger.error("AdmissionReview.request is null! Raw body was: {}", rawBody);
        throw new IllegalArgumentException("AdmissionReview request is null");
      }

      logger.info("Received admission review for UID: {}",
                  admissionReview.getRequest().getUid());

      // Parse the pod from the request
      V1Pod pod = objectMapper.readValue(
          objectMapper.writeValueAsString(admissionReview.getRequest().getObject()),
          V1Pod.class
      );

      logger.info("Processing pod: {}/{}",
                  pod.getMetadata().getNamespace(),
                  pod.getMetadata().getName());

      // Create the mutation response
      AdmissionReview response = mutationService.mutate(admissionReview, pod);

      logger.info("Successfully mutated pod: {}/{}",
                  pod.getMetadata().getNamespace(),
                  pod.getMetadata().getName());

      logger.info(response.toString());
      // Logs patch if present in mutation response
      if (response.getResponse() != null && response.getResponse().getPatch() != null) {
        logger.info(new String(response.getResponse().getPatch()));
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Error processing admission review", e);

      // Return an error response
      AdmissionReview errorResponse = new AdmissionReview();
      errorResponse.setApiVersion("admission.k8s.io/v1");
      errorResponse.setKind("AdmissionReview");

      AdmissionResponse response = new AdmissionResponse();

      // Safely get UID
      String uid = "unknown";
      if (admissionReview != null && admissionReview.getRequest() != null) {
        uid = admissionReview.getRequest().getUid();
      }
      response.setUid(uid);
      response.setAllowed(false);

      Status status = new Status();
      status.setMessage("Failed to process admission review: " + e.getMessage());
      status.setCode(500);
      response.setStatus(status);

      errorResponse.setResponse(response);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK");
  }

}
