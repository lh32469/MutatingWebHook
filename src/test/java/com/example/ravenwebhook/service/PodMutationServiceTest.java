package com.example.ravenwebhook.service;

import com.example.ravenwebhook.model.AdmissionReview;
import com.example.ravenwebhook.model.PatchOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PodMutationServiceTest {

    private PodMutationService mutationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mutationService = new PodMutationService(objectMapper);
    }

    @Test
    void testMutate_WithContainerWithoutEnvVars() throws Exception {
        // Create a pod with a container that has no env vars
        V1Pod pod = createTestPod("test-pod", "default");
        V1Container container = new V1Container()
            .name("nginx")
            .image("nginx:latest");
        pod.getSpec().containers(List.of(container));

        AdmissionReview request = createAdmissionReview(pod);

        // Execute mutation
        AdmissionReview response = mutationService.mutate(request, pod);

        // Verify response
        assertTrue(response.getResponse().isAllowed());
        assertNotNull(response.getResponse().getPatch());
        assertEquals("JSONPatch", response.getResponse().getPatchType());

        // Decode and verify patches
        String patchJson = new String(Base64.getDecoder().decode(response.getResponse().getPatch()));
        List<PatchOperation> patches = objectMapper.readValue(patchJson, new TypeReference<>() {});

        assertEquals(1, patches.size());
        assertEquals("add", patches.get(0).getOp());
        assertEquals("/spec/containers/0/env", patches.get(0).getPath());
    }

    @Test
    void testMutate_WithContainerWithExistingEnvVars() throws Exception {
        // Create a pod with a container that has existing env vars
        V1Pod pod = createTestPod("test-pod", "default");
        V1Container container = new V1Container()
            .name("nginx")
            .image("nginx:latest")
            .env(List.of(new V1EnvVar().name("EXISTING_VAR").value("existing_value")));
        pod.getSpec().containers(List.of(container));

        AdmissionReview request = createAdmissionReview(pod);

        // Execute mutation
        AdmissionReview response = mutationService.mutate(request, pod);

        // Decode and verify patches
        String patchJson = new String(Base64.getDecoder().decode(response.getResponse().getPatch()));
        List<PatchOperation> patches = objectMapper.readValue(patchJson, new TypeReference<>() {});

        assertEquals(1, patches.size());
        assertEquals("add", patches.get(0).getOp());
        assertEquals("/spec/containers/0/env/-", patches.get(0).getPath());
    }

    @Test
    void testMutate_WithContainerAlreadyHavingRavenUrl() throws Exception {
        // Create a pod with a container that already has RAVEN_URLS
        V1Pod pod = createTestPod("test-pod", "default");
        V1Container container = new V1Container()
            .name("nginx")
            .image("nginx:latest")
            .env(List.of(new V1EnvVar().name("RAVEN_URLS").value("existing")));
        pod.getSpec().containers(List.of(container));

        AdmissionReview request = createAdmissionReview(pod);

        // Execute mutation
        AdmissionReview response = mutationService.mutate(request, pod);

        // Verify no patches generated
        assertTrue(response.getResponse().isAllowed());
        assertNull(response.getResponse().getPatch());
    }

    @Test
    void testMutate_WithMultipleContainers() throws Exception {
        // Create a pod with multiple containers
        V1Pod pod = createTestPod("test-pod", "default");
        V1Container container1 = new V1Container()
            .name("nginx")
            .image("nginx:latest");
        V1Container container2 = new V1Container()
            .name("sidecar")
            .image("sidecar:latest");
        pod.getSpec().containers(List.of(container1, container2));

        AdmissionReview request = createAdmissionReview(pod);

        // Execute mutation
        AdmissionReview response = mutationService.mutate(request, pod);

        // Decode and verify patches
        String patchJson = new String(Base64.getDecoder().decode(response.getResponse().getPatch()));
        List<PatchOperation> patches = objectMapper.readValue(patchJson, new TypeReference<>() {});

        // Should have 2 patches, one for each container
        assertEquals(2, patches.size());
    }

    @Test
    void testMutate_WithInitContainers() throws Exception {
        // Create a pod with init containers
        V1Pod pod = createTestPod("test-pod", "default");
        V1Container initContainer = new V1Container()
            .name("init")
            .image("init:latest");
        V1Container container = new V1Container()
            .name("nginx")
            .image("nginx:latest");
        pod.getSpec()
            .initContainers(List.of(initContainer))
            .containers(List.of(container));

        AdmissionReview request = createAdmissionReview(pod);

        // Execute mutation
        AdmissionReview response = mutationService.mutate(request, pod);

        // Decode and verify patches
        String patchJson = new String(Base64.getDecoder().decode(response.getResponse().getPatch()));
        List<PatchOperation> patches = objectMapper.readValue(patchJson, new TypeReference<>() {});

        // Should have 2 patches: one for init container, one for regular container
        assertEquals(2, patches.size());
    }

    private V1Pod createTestPod(String name, String namespace) {
        return new V1Pod()
            .metadata(new V1ObjectMeta()
                .name(name)
                .namespace(namespace))
            .spec(new V1PodSpec()
                .containers(new ArrayList<>()));
    }

    private AdmissionReview createAdmissionReview(V1Pod pod) {
        AdmissionReview review = new AdmissionReview();
        review.setApiVersion("admission.k8s.io/v1");
        review.setKind("AdmissionReview");

        AdmissionReview.AdmissionRequest request = new AdmissionReview.AdmissionRequest();
        request.setUid("test-uid-123");
        request.setNamespace(pod.getMetadata().getNamespace());
        request.setName(pod.getMetadata().getName());
        request.setOperation("CREATE");

        review.setRequest(request);
        return review;
    }
}
