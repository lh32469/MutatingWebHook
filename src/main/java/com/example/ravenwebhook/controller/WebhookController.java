package com.example.ravenwebhook.controller;

import com.example.ravenwebhook.model.AdmissionReview;
import com.example.ravenwebhook.service.PodMutationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
  public ResponseEntity<AdmissionReview> mutate(@RequestBody AdmissionReview admissionReview) {
    try {
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

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Error processing admission review", e);

      // Return an error response
      AdmissionReview errorResponse = new AdmissionReview();
      errorResponse.setApiVersion("admission.k8s.io/v1");
      errorResponse.setKind("AdmissionReview");

      AdmissionReview.AdmissionResponse response =
          new AdmissionReview.AdmissionResponse();
      response.setUid(admissionReview.getRequest().getUid());
      response.setAllowed(false);
      response.setStatus(new AdmissionReview.Status(
          "Failure",
          "Failed to process admission review: " + e.getMessage(),
          500
      ));

      errorResponse.setResponse(response);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK");
  }

}
