package cloud.c4h.platform.service;

import cloud.c4h.platform.domain.Operino;

import java.util.Map;

public interface OperinoConfiguration{
    Map<String, String> getConfigForOperino(Operino operino);
}
