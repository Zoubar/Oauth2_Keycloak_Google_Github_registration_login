package ma.enset.customerfrontthymeleafapp.sec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        return  http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(ar->ar.requestMatchers("/","/oauth2Login/**","/webjars/**","/h2-console/**").permitAll())
                .authorizeHttpRequests(ar->ar.anyRequest().authenticated())
                .oauth2Login(al->
                        al.loginPage("/oauth2Login")
                                .defaultSuccessUrl("/")
                )
                .logout((logout) -> logout
                        .logoutSuccessHandler(oidcClientInitiatedLogoutSuccessHandler())
                        .logoutSuccessUrl("/").permitAll().clearAuthentication(true).deleteCookies("JSESSIONID"))
                .exceptionHandling(eh->eh.accessDeniedPage("/notAutorized"))
                .build();

    }

    private OidcClientInitiatedLogoutSuccessHandler oidcClientInitiatedLogoutSuccessHandler()
    {
        final OidcClientInitiatedLogoutSuccessHandler oidcClientInitiatedLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        oidcClientInitiatedLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}?logoutsuccess=true");
        return oidcClientInitiatedLogoutSuccessHandler;
    }

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper()
    {
        return (authorities) -> {

            final Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            authorities.forEach((authority) -> {
                if(authority instanceof OidcUserAuthority oidcUserAuthority) {
                    mappedAuthorities.addAll(mapAuthorities(oidcUserAuthority.getIdToken().getClaims()));
                    System.out.println(oidcUserAuthority.getAttributes());
                }else if (authority instanceof OAuth2UserAuthority oAuth2UserAuthority)
                {
                    mappedAuthorities.addAll(mapAuthorities(oAuth2UserAuthority.getAttributes()));
                }
            });
            return mappedAuthorities;
        };
    }

    private List<SimpleGrantedAuthority> mapAuthorities(final Map<String,Object> attributes)
    {
        final Map<String , Object> realmAccess = ((Map<String , Object>)attributes.getOrDefault("realm_access", Collections.emptyMap()));
        final Collection<String> roles = ((Collection<String>)realmAccess.getOrDefault("roles" , Collections.emptyList()));
        return roles.stream()
                .map((role)->new SimpleGrantedAuthority(role))
                .toList();
    }
}
