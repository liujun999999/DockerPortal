package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 容器内文件浏览与文本编辑。
 * <p>
 * 列目录用 {@code docker exec}（仅对单层做 stat，不传输文件内容）；
 * 读/写文件用 Docker archive（tar 归档）接口，读取上限 1MB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerFileService {

    private static final long MAX_READ_BYTES = 1024 * 1024;   // 预览上限 1MB
    private static final long MAX_WRITE_BYTES = 2 * 1024 * 1024;
    private static final int EXEC_TIMEOUT_SEC = 15;

    /**
     * 单层列目录脚本：$1 为目标目录（作为位置参数传入，避免注入）。
     * 输出每行 "类型\t大小\t名称"，类型 d=目录 f=文件；先打印哨兵区分"目录不可进入"与"空目录"。
     */
    private static final String LIST_SCRIPT =
            "cd \"$1\" 2>/dev/null || { echo '__DP_ERR__'; exit 0; }\n"
          + "echo '__DP_OK__'\n"
          + "for f in * .[!.]* ..?*; do\n"
          + "  [ -e \"$f\" ] || [ -L \"$f\" ] || continue\n"
          + "  if [ -d \"$f\" ] && [ ! -L \"$f\" ]; then\n"
          + "    printf 'd\\t0\\t%s\\n' \"$f\"\n"
          + "  else\n"
          + "    s=$(stat -c %s \"$f\" 2>/dev/null || echo 0)\n"
          + "    printf 'f\\t%s\\t%s\\n' \"$s\" \"$f\"\n"
          + "  fi\n"
          + "done\n";

    private final DockerClientManager dockerClientManager;

    /**
     * 列出目录的直接子项（懒加载，仅单层，不下载文件内容）
     */
    public List<Map<String, Object>> listDir(Long envId, String containerId, String path) {
        String dir = normalizeDir(path);
        DockerClient client = dockerClientManager.getClient(envId);
        String out = execCapture(client, containerId,
                new String[]{"sh", "-c", LIST_SCRIPT, "sh", dir});

        if (out == null || out.contains("__DP_ERR__") || !out.contains("__DP_OK__")) {
            throw new BusinessException(40400, "无法进入目录（不存在、无权限或容器内无 sh）：" + dir);
        }
        String body = out.substring(out.indexOf("__DP_OK__") + "__DP_OK__".length());

        List<Map<String, Object>> children = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.isEmpty()) continue;
            // 以制表符分隔，名称可能含空格但不含制表符/换行
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            boolean isDir = "d".equals(parts[0]);
            long size;
            try {
                size = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException e) {
                size = 0;
            }
            String name = parts[2];
            if (name.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("path", joinPath(dir, name));
            row.put("dir", isDir);
            row.put("size", isDir ? null : size);
            children.add(row);
        }
        children.sort(Comparator
                .comparing((Map<String, Object> r) -> Boolean.TRUE.equals(r.get("dir")) ? 0 : 1)
                .thenComparing(r -> String.valueOf(r.get("name")).toLowerCase()));
        return children;
    }

    /**
     * 读取文本文件内容
     */
    public Map<String, Object> readFile(Long envId, String containerId, String path) {
        DockerClient client = dockerClientManager.getClient(envId);
        try (InputStream tar = client.copyArchiveFromContainerCmd(containerId, path).exec();
             TarArchiveInputStream in = new TarArchiveInputStream(tar)) {
            TarArchiveEntry entry = in.getNextTarEntry();
            if (entry == null) {
                throw new BusinessException(40400, "文件不存在：" + path);
            }
            if (entry.isDirectory()) {
                throw new BusinessException(40000, "这是一个目录，无法作为文件打开：" + path);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            boolean truncated = false;
            boolean binary = false;
            while ((len = in.read(buf)) != -1) {
                for (int i = 0; i < len; i++) {
                    if (buf[i] == 0) { binary = true; break; }
                }
                if (binary) break;
                if (total + len > MAX_READ_BYTES) {
                    bos.write(buf, 0, (int) (MAX_READ_BYTES - total));
                    truncated = true;
                    break;
                }
                bos.write(buf, 0, len);
                total += len;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("size", entry.getSize());
            result.put("binary", binary);
            result.put("truncated", truncated);
            result.put("content", binary ? null : bos.toString(StandardCharsets.UTF_8));
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            throw new BusinessException(40400, "文件不存在：" + path);
        } catch (ConflictException e) {
            throw new BusinessException(40900, "容器未运行，无法访问文件");
        } catch (IOException e) {
            throw new BusinessException(50000, "读取文件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 写入（覆盖）文本文件
     */
    public void writeFile(Long envId, String containerId, String path, String content) {
        String clean = path == null ? "" : path.trim();
        if (clean.isEmpty() || clean.endsWith("/")) {
            throw new BusinessException(40000, "文件路径无效");
        }
        int idx = clean.lastIndexOf('/');
        String parent = idx <= 0 ? "/" : clean.substring(0, idx);
        String name = clean.substring(idx + 1);
        byte[] data = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_WRITE_BYTES) {
            throw new BusinessException(40000, "内容过大，无法通过此方式保存（上限 2MB）");
        }

        ByteArrayOutputStream tarBuf = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(tarBuf)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(data.length);
            entry.setMode(0100644);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
            tar.finish();
        } catch (IOException e) {
            throw new BusinessException(50000, "打包文件失败：" + e.getMessage(), e);
        }

        DockerClient client = dockerClientManager.getClient(envId);
        try (InputStream in = new ByteArrayInputStream(tarBuf.toByteArray())) {
            client.copyArchiveToContainerCmd(containerId)
                    .withRemotePath(parent)
                    .withTarInputStream(in)
                    .withNoOverwriteDirNonDir(false)
                    .exec();
            log.info("Wrote file {} ({} bytes) in container {}", clean, data.length, containerId);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            throw new BusinessException(40400, "容器不存在或已删除");
        } catch (ConflictException e) {
            throw new BusinessException(40900, "容器未运行，无法写入文件");
        } catch (com.github.dockerjava.api.exception.InternalServerErrorException e) {
            throw new BusinessException(50001, "写入失败（可能是只读挂载或权限不足）：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new BusinessException(50000, "写入文件失败：" + e.getMessage(), e);
        }
    }

    /* -------------------- 内部工具 -------------------- */

    /**
     * 在容器内执行命令并捕获 stdout（不占 tty，避免 ANSI 干扰解析）
     */
    private String execCapture(DockerClient client, String containerId, String[] cmd) {
        ExecCreateCmdResponse exec;
        try {
            exec = client.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .withAttachStderr(false)
                    .withTty(false)
                    .exec();
        } catch (ConflictException e) {
            throw new BusinessException(40900, "容器未运行，无法浏览文件");
        }
        StringBuilder stdout = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            client.execStartCmd(exec.getId())
                    .withTty(false)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                stdout.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                        }
                    })
                    .awaitCompletion(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(50000, "列目录超时");
        }
        if (error.get() != null) {
            throw new BusinessException(50002, "容器命令执行失败：" + error.get().getMessage());
        }
        return stdout.toString();
    }

    private String normalizeDir(String path) {
        String p = path == null || path.isBlank() ? "/" : path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private String joinPath(String dir, String name) {
        return "/".equals(dir) ? "/" + name : dir + "/" + name;
    }
}
