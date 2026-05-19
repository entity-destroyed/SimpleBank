package hu.bme.aut.simplebank.controller.mapper;

import hu.bme.aut.simplebank.controller.dto.user.UserResponse;
import hu.bme.aut.simplebank.entity.AppUser;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(AppUser user);

    List<UserResponse> toResponseList(List<AppUser> users);
}
