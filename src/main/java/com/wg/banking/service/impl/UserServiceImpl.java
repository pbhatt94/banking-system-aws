package com.wg.banking.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wg.banking.model.*;
import com.wg.banking.service.SQSMessagePublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wg.banking.constants.ApiMessages;
import com.wg.banking.dto.UserDto;
import com.wg.banking.exception.AdminAccountExistsException;
import com.wg.banking.exception.UserNotFoundException;
import com.wg.banking.filter.UsersFilter;
import com.wg.banking.mapper.UserMapper;
import com.wg.banking.repository.UserRepository;
import com.wg.banking.service.AccountService;
import com.wg.banking.service.UserService; 
import com.wg.banking.specification.UserSpec;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
	private final UserRepository userRepository;
	private final AccountService accountService;
	private final PasswordEncoder passwordEncoder;
	private final SQSMessagePublisher sqsMessagePublisher;

	@Override
	public List<UserDto> findAllUsers(UsersFilter filter, Integer pageNumber, Integer pageSize) {
		if (pageNumber <= 0 || pageSize <= 0)
			throw new InvalidInputException(ApiMessages.INVALID_PAGE_NUMBER_OR_LIMIT);
		pageNumber--;
		
		Specification<User> spec = UserSpec.filterBy(filter);
		Page<User> page = userRepository.findAll(spec, (Pageable) PageRequest.of(pageNumber, pageSize));
		List<User> users = page.getContent();
		List<UserDto> userDtos = new ArrayList<>();
		for (User user : users) {
			userDtos.add(UserMapper.mapUser(user));
		}
		return userDtos;
	}

	@Override
	public UserDto createUser(User user) {
		if (isRoleAdmin(user)) {
			throw new AdminAccountExistsException(ApiMessages.ADMIN_ALREADY_EXISTS_MESSAGE);
		}
		User savedUser = userRepository.save(user);
		if (isCustomer(savedUser)) {
			Account account = accountService.createAccount(savedUser);
			savedUser.setAccount(account);
			savedUser = userRepository.save(savedUser);
		}
		UserDto userDto = UserMapper.mapUser(savedUser);
		Message message = Message.builder()
								.id(UUID.randomUUID().toString())
								.type(MessageType.USER_SIGNUP)
								.receiver(savedUser.getEmail())
								.content(ApiMessages.USER_CREATED_SUCCESSFULLY)
								.createdAt(LocalDateTime.now())
								.build();
		sqsMessagePublisher.publishMessage(message);
		return userDto;
	}

	private boolean isRoleAdmin(User user) {
		return user.getRole() != null && user.getRole().equals(Role.ADMIN);
	}

	@Override
	public UserDto findUserById(String userId) {
		Optional<User> user = userRepository.findById(userId);
		if (!user.isPresent())
			throw new UserNotFoundException(ApiMessages.USER_NOT_FOUND_ERROR + ": " + userId);
		return UserMapper.mapUser(user.get());
	}

	@Override
	public boolean deleteUserById(String userId) {
		Optional<User> user = userRepository.findById(userId);
		if (user.isEmpty())
			return false;
		else if (isAdmin(user))
			return false;
		userRepository.deleteById(userId);
		return true;
	}

	@Override
	public User updateUserById(String userId, User userDetails) {
		Optional<User> existingUser = userRepository.findById(userId);
		if (existingUser.isEmpty()) {
			throw new UserNotFoundException(ApiMessages.USER_NOT_FOUND_ERROR + ": " + userId);
		}
		User user = existingUser.get();
		mapUpdatedUser(userId, userDetails, user);
		return userRepository.save(user);
	}

	@Override
	public long countAllUsers() {
		long count = userRepository.count();
		return count;
	}

	public User getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication != null && authentication.isAuthenticated()) {
			String username = authentication.getName();
			return userRepository.findByUsername(username)
					.orElseThrow(() -> new UserNotFoundException(ApiMessages.USER_NOT_FOUND_ERROR + ": " + username));
		}

		return null;
	}

	private void mapUpdatedUser(String userId, User userDetails, User user) {
		user.setUserId(userId);
		user.setAge(userDetails.getAge());
		user.setEmail(userDetails.getEmail());
		user.setName(userDetails.getName());
		user.setAddress(userDetails.getAddress());
		user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
		user.setPhoneNo(userDetails.getPhoneNo());
		user.setUpdatedAt(LocalDateTime.now());
		user.setUsername(userDetails.getUsername());
	}

	private boolean isAdmin(Optional<User> user) {
		return user.get().getRole().equals(Role.ADMIN);
	}

	private boolean isCustomer(User user) {
		return user.getRole().equals(Role.CUSTOMER);
	}
}