package io.realworld.app.domain

import io.javalin.BadRequestResponse
import io.javalin.HttpResponseException
import io.javalin.NotFoundResponse
import io.javalin.UnauthorizedResponse
import io.realworld.app.config.Roles
import io.realworld.app.domain.repository.UserRepository
import io.realworld.app.utils.Cipher
import io.realworld.app.utils.JwtProvider
import org.eclipse.jetty.http.HttpStatus
import java.util.*

data class UserDTO(val user: User? = null)

data class User(val id: Long? = null,
                val email: String,
                val token: String? = null,
                val username: String? = null,
                val password: String? = null,
                val bio: String? = null,
                val image: String? = null)

class UserService(private val jwtProvider: JwtProvider, private val userRepository: UserRepository) {

    private val base64Encoder = Base64.getEncoder()

    fun create(user: User): User {
        userRepository.findByEmail(user.email).takeIf { it != null }?.apply {
            throw HttpResponseException(HttpStatus.BAD_REQUEST_400,
                    "Email already registered!")
        }
        userRepository.create(user.copy(password = String(base64Encoder.encode(Cipher.encrypt(user.password)))))
        return user
    }

    fun authenticate(user: User): User {
        val userFound = userRepository.findByEmail(user.email)
        if (userFound?.password == String(base64Encoder.encode(Cipher.encrypt(user.password)))) {
            return userFound.copy(token = generateJwtToken(userFound))
        }
        throw UnauthorizedResponse("email or password invalid!")
    }

    fun getByEmail(email: String?): User {
        if (email.isNullOrBlank()) throw BadRequestResponse()
        val user = userRepository.findByEmail(email!!)
        user ?: throw NotFoundResponse()
        return user.copy(token = generateJwtToken(user))
    }

    fun getProfileByUsername(email: String, usernameFollowing: String?): Profile {
        if (usernameFollowing == null || usernameFollowing.isNullOrBlank()) throw BadRequestResponse()
        return userRepository.findByUsername(usernameFollowing).let { user ->
            user ?: throw NotFoundResponse()
            Profile(user.username, user.bio, user.image, userRepository.findIsFollowUser(email, user.id!!))
        }
    }

    fun update(email: String?, user: User): User? {
        email ?: throw HttpResponseException(HttpStatus.NOT_ACCEPTABLE_406, "User not found to update.")
        return userRepository.update(email, user)
    }

    private fun generateJwtToken(user: User): String? {
        return jwtProvider.createJWT(user, Roles.AUTHENTICATED)
    }

    fun follow(email: String, usernameToFollow: String): Profile {
        if (usernameToFollow.isNullOrBlank()) throw BadRequestResponse()
        return userRepository.follow(email, usernameToFollow).let { user ->
            user ?: throw NotFoundResponse()
            Profile(user.username, user.bio, user.image, true)
        }
    }

    fun unfollow(email: String, usernameToUnfollow: String): Profile {
        if (usernameToUnfollow.isNullOrBlank()) throw BadRequestResponse()
        return userRepository.unfollow(email, usernameToUnfollow).let { user ->
            user ?: throw NotFoundResponse()
            Profile(user.username, user.bio, user.image, false)
        }
    }
}