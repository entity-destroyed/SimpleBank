package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.user.CreateUserRequest;
import hu.bme.aut.simplebank.controller.dto.user.UpdateProfileRequest;
import hu.bme.aut.simplebank.controller.dto.user.UserResponse;
import hu.bme.aut.simplebank.controller.mapper.UserMapper;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.exception.ConflictException;
import hu.bme.aut.simplebank.exception.ResourceNotFoundException;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.security.UserDetailsImpl;
import hu.bme.aut.simplebank.util.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AppUserService service;

    private AppUser adminEntity;
    private AppUser clientEntity;
    private UserDetailsImpl adminPrincipal;
    private UserDetailsImpl clientPrincipal;

    @BeforeEach
    void setUp() {
        adminEntity = TestEntities.user(1L, "admin@bank.local", AppUser.Role.ADMIN);
        clientEntity = TestEntities.user(2L, "client@bank.local", AppUser.Role.CLIENT);
        adminPrincipal = new UserDetailsImpl(adminEntity);
        clientPrincipal = new UserDetailsImpl(clientEntity);
    }


    @Test
    void registerHappyPath() {
        CreateUserRequest req = new CreateUserRequest("New", "new@bank.local", "secret123", AppUser.Role.CLIENT);
        when(userRepository.existsByEmail("new@bank.local")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        AppUser saved = new AppUser();
        saved.setId(10L);
        saved.setName("New");
        saved.setEmail("new@bank.local");
        saved.setPasswordHash("encoded");
        saved.setRole(AppUser.Role.CLIENT);
        when(userRepository.save(any(AppUser.class))).thenReturn(saved);
        UserResponse expected = new UserResponse(10L, "New", "new@bank.local", AppUser.Role.CLIENT);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse actual = service.register(req, adminPrincipal);

        assertEquals(expected, actual);
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertEquals("encoded", captor.getValue().getPasswordHash());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        CreateUserRequest req = new CreateUserRequest("Dup", "dup@bank.local", "secret123", AppUser.Role.CLIENT);
        when(userRepository.existsByEmail("dup@bank.local")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.register(req, adminPrincipal));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerForbiddenForClient() {
        CreateUserRequest req = new CreateUserRequest("X", "x@bank.local", "secret123", AppUser.Role.CLIENT);
        assertThrows(AccessDeniedException.class, () -> service.register(req, clientPrincipal));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerForbiddenForAnonymous() {
        CreateUserRequest req = new CreateUserRequest("X", "x@bank.local", "secret123", AppUser.Role.CLIENT);
        assertThrows(AccessDeniedException.class, () -> service.register(req, null));
    }


    @Test
    void findAllHappyPath() {
        when(userRepository.findAll()).thenReturn(List.of(adminEntity, clientEntity));
        when(userMapper.toResponseList(List.of(adminEntity, clientEntity)))
                .thenReturn(List.of(
                        new UserResponse(1L, "Admin", "admin@bank.local", AppUser.Role.ADMIN),
                        new UserResponse(2L, "Client", "client@bank.local", AppUser.Role.CLIENT)));

        List<UserResponse> result = service.findAll(adminPrincipal);

        assertEquals(2, result.size());
    }

    @Test
    void findAllForbiddenForClient() {
        assertThrows(AccessDeniedException.class, () -> service.findAll(clientPrincipal));
    }


    @Test
    void findByIdHappyPath() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(clientEntity));
        UserResponse expected = new UserResponse(2L, "Client", "client@bank.local", AppUser.Role.CLIENT);
        when(userMapper.toResponse(clientEntity)).thenReturn(expected);

        UserResponse result = service.findById(2L, adminPrincipal);

        assertEquals(expected, result);
    }

    @Test
    void findByIdReturnsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L, adminPrincipal));
    }

    @Test
    void findByIdForbiddenForClient() {
        assertThrows(AccessDeniedException.class, () -> service.findById(2L, clientPrincipal));
    }


    @Test
    void getOwnProfileReturnsCallerEntity() {
        UserResponse expected = new UserResponse(2L, "Client", "client@bank.local", AppUser.Role.CLIENT);
        when(userMapper.toResponse(clientEntity)).thenReturn(expected);

        UserResponse result = service.getOwnProfile(clientPrincipal);

        assertEquals(expected, result);
    }

    @Test
    void getOwnProfileRejectsForeignPrincipalType() {
        UserDetails plain = User.withUsername("anon").password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT")).build();
        assertThrows(AccessDeniedException.class, () -> service.getOwnProfile(plain));
    }


    @Test
    void updateOwnProfileChangesNameAndEmail() {
        UpdateProfileRequest req = new UpdateProfileRequest("New Name", "new-email@bank.local");
        when(userRepository.findById(2L)).thenReturn(Optional.of(clientEntity));
        when(userRepository.existsByEmail("new-email@bank.local")).thenReturn(false);
        when(userRepository.save(clientEntity)).thenReturn(clientEntity);
        UserResponse expected = new UserResponse(2L, "New Name", "new-email@bank.local", AppUser.Role.CLIENT);
        when(userMapper.toResponse(clientEntity)).thenReturn(expected);

        UserResponse result = service.updateOwnProfile(clientPrincipal, req);

        assertEquals(expected, result);
        assertEquals("New Name", clientEntity.getName());
        assertEquals("new-email@bank.local", clientEntity.getEmail());
    }

    @Test
    void updateOwnProfileAllowsKeepingSameEmail() {
        UpdateProfileRequest req = new UpdateProfileRequest("Renamed", "client@bank.local");
        when(userRepository.findById(2L)).thenReturn(Optional.of(clientEntity));
        when(userRepository.save(clientEntity)).thenReturn(clientEntity);
        UserResponse expected = new UserResponse(2L, "Renamed", "client@bank.local", AppUser.Role.CLIENT);
        when(userMapper.toResponse(clientEntity)).thenReturn(expected);

        UserResponse result = service.updateOwnProfile(clientPrincipal, req);

        assertEquals(expected, result);
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateOwnProfileRejectsConflictingEmail() {
        UpdateProfileRequest req = new UpdateProfileRequest("X", "taken@bank.local");
        when(userRepository.findById(2L)).thenReturn(Optional.of(clientEntity));
        when(userRepository.existsByEmail("taken@bank.local")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.updateOwnProfile(clientPrincipal, req));
        verify(userRepository, never()).save(any());
    }


    @Test
    void deleteUserHappyPath() {
        when(userRepository.existsById(2L)).thenReturn(true);

        service.deleteUser(2L, adminPrincipal);

        verify(userRepository).deleteById(2L);
    }

    @Test
    void deleteUserReturnsNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteUser(99L, adminPrincipal));
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUserForbiddenForClient() {
        assertThrows(AccessDeniedException.class, () -> service.deleteUser(2L, clientPrincipal));
        verify(userRepository, never()).deleteById(any());
    }
}
