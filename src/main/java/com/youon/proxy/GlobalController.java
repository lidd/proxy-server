package com.youon.proxy;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Map;

@Controller
@RequestMapping("/")
public class GlobalController {

    @Value("${remote.host}")
    private String remote;

    @RequestMapping("/api/**")
    public void dispatch(HttpServletRequest request, HttpServletResponse response) {
        try (CloseableHttpClient closeableHttpClient = HttpClients.createDefault()) {
            String method = request.getMethod();
            HttpContext context = setContext(request);
            HttpRequest httpRequest;
            if ("post".equalsIgnoreCase(method)) {
                httpRequest = new HttpPost(buildURI(request));
            } else {
                httpRequest = new HttpGet(buildURI(request));
            }
            transferRequestHeaders(httpRequest, request);
            CloseableHttpResponse httpResponse = closeableHttpClient
                    .execute(HttpHost.create(remote), httpRequest, context);
            transferResponseHeaders(httpResponse, response);
            httpResponse.getEntity().writeTo(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/workspace/**")
    public String selfJump(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI().substring(11);
        if (uri.endsWith(".js")
                || uri.endsWith(".html")
                || uri.endsWith(".png")
                || uri.endsWith("ico")) {
            return "redirect:/" + uri;
        }
        return "forward:/index.html";
    }

    private HttpClientContext setContext(HttpServletRequest request) {
        HttpClientContext context = HttpClientContext.create();
        Registry<CookieSpecProvider> registry = RegistryBuilder
                .<CookieSpecProvider>create()
                .register(CookieSpecs.DEFAULT, new DefaultCookieSpecProvider())
                .build();
        context.setCookieSpecRegistry(registry);
        context.setCookieStore(setCookieStore(request));
        return context;
    }

    private CookieStore setCookieStore(HttpServletRequest request) {
        BasicCookieStore cookieStore = new BasicCookieStore();
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
            for (Cookie cookie : cookies) {
                cookieStore.addCookie(new BasicClientCookie(cookie.getName(), cookie.getValue()));
            }
        return cookieStore;
    }

    private URI buildURI(HttpServletRequest request) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(request.getRequestURI().substring(4));
        Map<String, String[]> map = request.getParameterMap();
        for (Map.Entry<String, String[]> stringEntry : map.entrySet()) {
            builder.addParameter(stringEntry.getKey(),
                    StringUtils.arrayToDelimitedString(stringEntry.getValue(), ","));
        }
        return builder.build();
    }

    private void transferResponseHeaders(CloseableHttpResponse httpResponse, HttpServletResponse servletResponse) {
        Header[] headers = httpResponse.getAllHeaders();
        for (Header header : headers) {
            servletResponse.addHeader(header.getName(), header.getValue());
        }
    }

    private void transferRequestHeaders(HttpRequest httpRequest, HttpServletRequest servletRequest) {
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.equalsIgnoreCase(HTTP.CONTENT_LEN)
                    || name.equalsIgnoreCase(HTTP.TRANSFER_ENCODING)) {
                continue;
            }
            String value = servletRequest.getHeader(name);
            httpRequest.setHeader(name, value);
        }
    }
}
