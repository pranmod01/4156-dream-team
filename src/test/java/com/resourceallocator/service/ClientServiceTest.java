package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.model.Organization;
import com.resourceallocator.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(organizationRepository);
    }

    @Test
    void createClientStoresOnlyTheKeyHash() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        3L));

        CreateClientResponse response = clientService.createClient(
                new CreateClientRequest("acme", 8, 2));

        ArgumentCaptor<Organization> saved =
                ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).saveAndFlush(saved.capture());
        assertThat(response.apiKey()).isNotBlank();
        assertThat(saved.getValue().getApiKeyHash())
                .isEqualTo(ApiKeyHasher.hash(response.apiKey()))
                .isNotEqualTo(response.apiKey());
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.orgName()).isEqualTo("acme");
        assertThat(response.maxCpus()).isEqualTo(8);
        assertThat(response.maxGpus()).isEqualTo(2);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void createClientIssuesDifferentKeysEachTime() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String first = clientService.createClient(
                new CreateClientRequest("a", 1, 1)).apiKey();
        String second = clientService.createClient(
                new CreateClientRequest("b", 1, 1)).apiKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createClientTrimsName() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientResponse response = clientService.createClient(
                new CreateClientRequest("  acme  ", 1, 1));

        verify(organizationRepository).existsByOrgName("acme");
        assertThat(response.orgName()).isEqualTo("acme");
    }

    @Test
    void createClientRejectsTakenName() {
        when(organizationRepository.existsByOrgName("acme")).thenReturn(true);
        CreateClientRequest request = new CreateClientRequest("acme", 1, 1);

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("acme");
        verify(organizationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createClientTurnsUniqueViolationIntoConflict() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        CreateClientRequest request = new CreateClientRequest("acme", 1, 1);

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ConflictException.class);
    }

    private static Organization withId(final Organization organization,
            final long id) {
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }
}
