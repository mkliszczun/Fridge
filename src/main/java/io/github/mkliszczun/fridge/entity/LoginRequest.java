package io.github.mkliszczun.fridge.entity;

public class LoginRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 254)
    private String login;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 72)
    private String password;

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }
}
