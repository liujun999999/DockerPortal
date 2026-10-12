package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.dto.UserCreateRequest;
import com.dockerportal.model.dto.UserDTO;
import com.dockerportal.model.entity.User;
import com.dockerportal.model.enums.UserRole;
import com.dockerportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO> listAll() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public UserDTO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        return toDTO(user);
    }

    public UserDTO findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        return toDTO(user);
    }

    public UserDTO create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(40000, "用户名已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole())
                .mustChangePassword(true)
                .build();
        user = userRepository.save(user);
        log.info("User created: {}", user.getUsername());
        return toDTO(user);
    }

    /**
     * 首次安装创建管理员账号。与 {@link #create} 的区别只在语义：密码强度复用
     * {@link #validateStrength}（规则不另开一份），mustChangePassword 为 false —— 密码是用户
     * 此刻自己在浏览器里设的，不存在"默认密码待改"的问题。
     */
    public UserDTO createInitialAdmin(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(40000, "用户名已存在");
        }
        validateStrength(rawPassword);
        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .email(username + "@dockerportal.local")
                .role(UserRole.ADMIN)
                .enabled(true)
                .mustChangePassword(false)
                .build();
        admin = userRepository.save(admin);
        log.info("Initial admin created by setup wizard: {}", admin.getUsername());
        return toDTO(admin);
    }

    /** 自助改密：校验旧密码，成功后清除首登标记 */
    public void changeOwnPassword(String username, String oldPwd, String newPwd) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            throw new BusinessException(40010, "旧密码不正确");
        }
        validateStrength(newPwd);
        if (oldPwd != null && oldPwd.equals(newPwd)) {
            throw new BusinessException(40011, "新密码不能与旧密码相同");
        }
        user.setPassword(passwordEncoder.encode(newPwd));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("User changed own password: {}", username);
    }

    /** 管理员重置密码：置首登强制改密标记 */
    public UserDTO resetPassword(Long id, String newPwd) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        validateStrength(newPwd);
        user.setPassword(passwordEncoder.encode(newPwd));
        user.setMustChangePassword(true);
        user = userRepository.save(user);
        log.info("Admin reset password for user: {}", user.getUsername());
        return toDTO(user);
    }

    private void validateStrength(String pwd) {
        if (pwd == null || pwd.length() < 8) {
            throw new BusinessException(40012, "密码长度至少 8 位");
        }
        boolean hasLetter = pwd.chars().anyMatch(Character::isLetter);
        boolean hasDigit = pwd.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(40013, "密码需同时包含字母和数字");
        }
    }

    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        userRepository.delete(user);
        log.info("User deleted: {}", user.getUsername());
    }

    public UserDTO toggleEnabled(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
        user.setEnabled(!user.getEnabled());
        user = userRepository.save(user);
        log.info("User {} {}", user.getUsername(), user.getEnabled() ? "enabled" : "disabled");
        return toDTO(user);
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
