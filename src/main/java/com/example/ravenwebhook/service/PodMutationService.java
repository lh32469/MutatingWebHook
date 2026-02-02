package com.example.ravenwebhook.service;

import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import com.example.ravenwebhook.model.PatchOperation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PodMutationService {

  private static final Logger logger = LoggerFactory.getLogger(PodMutationService.class);
  private static final String ENV_VAR_NAME = "RAVEN_URLS";
  private static final String ENV_VAR_VALUE = "foo";

  private final ObjectMapper objectMapper;

  public PodMutationService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AdmissionReview mutate(AdmissionReview admissionReview, V1Pod pod) throws
      JsonProcessingException {
    List<PatchOperation> patches = createPatches(pod);

    AdmissionReview response = new AdmissionReview();
    response.setApiVersion("admission.k8s.io/v1");
    response.setKind("AdmissionReview");

    AdmissionResponse admissionResponse = new AdmissionResponse();
    admissionResponse.setUid(admissionReview.getRequest().getUid());
    admissionResponse.setAllowed(true);

    if (!patches.isEmpty()) {
      String patchJson = objectMapper.writeValueAsString(patches);
      admissionResponse.setPatch(patchJson.getBytes());
      admissionResponse.setPatchType("JSONPatch");
      logger.info("Generated {} patches for pod", patches.size());
    } else {
      logger.info("No patches needed for pod");
    }

    response.setResponse(admissionResponse);
    return response;
  }

  private List<PatchOperation> createPatches(V1Pod pod) {
    List<PatchOperation> patches = new ArrayList<>();

    // Process regular containers
    if (pod.getSpec().getContainers() != null) {
      for (int i = 0; i < pod.getSpec().getContainers().size(); i++) {
        V1Container container = pod.getSpec().getContainers().get(i);
        patches.addAll(createContainerPatches(container, i, "containers"));
      }
    }

    // Process init containers
    if (pod.getSpec().getInitContainers() != null) {
      for (int i = 0; i < pod.getSpec().getInitContainers().size(); i++) {
        V1Container container = pod.getSpec().getInitContainers().get(i);
        patches.addAll(createContainerPatches(container, i, "initContainers"));
      }
    }

    return patches;
  }

  private List<PatchOperation> createContainerPatches(V1Container container, int index,
                                                      String containerType) {
    List<PatchOperation> patches = new ArrayList<>();

    if (hasEnvVar(container, ENV_VAR_NAME)) {
      logger.debug("Container {} already has {} env var",
                   container.getName(),
                   ENV_VAR_NAME);
      return patches;
    }

    V1EnvVar ravenEnv = new V1EnvVar()
        .name(ENV_VAR_NAME)
        .value(ENV_VAR_VALUE);

    V1EnvVar fooEnv = new V1EnvVar()
        .name("FOO")
        .value("Bar");

    String basePath = String.format("/spec/%s/%d/env", containerType, index);

    if (container.getEnv() == null || container.getEnv().isEmpty()) {
      // Create the env array with the new variable
      List<V1EnvVar> envVars = new ArrayList<>();
      envVars.add(ravenEnv);
      envVars.add(fooEnv);
      patches.add(new PatchOperation("add", basePath, envVars));
      logger.debug("Creating env array for container {}", container.getName());
    } else {
      // Append to existing env array
      patches.add(new PatchOperation("add", basePath + "/-", ravenEnv));
      logger.debug("Appending to env array for container {}", container.getName());
    }

    return patches;
  }

  private boolean hasEnvVar(V1Container container, String name) {
    if (container.getEnv() == null) {
      return false;
    }

    return container.getEnv().stream()
                    .anyMatch(env -> name.equals(env.getName()));
  }

}
