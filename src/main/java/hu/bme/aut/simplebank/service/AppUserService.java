package hu.bme.aut.simplebank.service;

import hu.bme.aut.simplebank.controller.dto.user.CreateUserRequest;
import hu.bme.aut.simplebank.controller.dto.user.UpdateProfileRequest;
import hu.bme.aut.simplebank.controller.dto.user.UserResponse;
import hu.bme.aut.simplebank.controller.mapper.UserMapper;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.exception.ConflictException;
import hu.bme.aut.simplebank.exception.ResourceNotFoundException;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.util.AuthUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AppUserService(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(CreateUserRequest request, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        AppUser user = new AppUser();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        AppUser saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        return userMapper.toResponseList(userRepository.findAll());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getOwnProfile(UserDetails caller) {
        AppUser user = AuthUtils.requireCurrentUser(caller);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateOwnProfile(UserDetails caller, UpdateProfileRequest request) {
        AppUser user = AuthUtils.requireCurrentUser(caller);
        AppUser managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user.getId()));
        if (!managed.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        managed.setName(request.name());
        managed.setEmail(request.email());
        return userMapper.toResponse(userRepository.save(managed));
    }

    @Transactional
    public void deleteUser(Long id, UserDetails caller) {
        AuthUtils.requireAdmin(caller);
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }
}
