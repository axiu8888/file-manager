package com.kiftd.webdav;

import com.kiftd.entity.FileNode;
import com.kiftd.entity.Folder;
import com.kiftd.repository.FileNodeRepository;
import com.kiftd.repository.FolderRepository;
import com.kiftd.util.IdUtil;
import com.kiftd.util.StorageService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * Minimal WebDAV (PROPFIND/GET/PUT/DELETE/MKCOL) mapped under /webdav/*
 */
@Component
public class WebDavServlet extends HttpServlet {

    private final FolderRepository folderRepository;
    private final FileNodeRepository fileNodeRepository;
    private final StorageService storageService;

    public WebDavServlet(FolderRepository folderRepository, FileNodeRepository fileNodeRepository,
                         StorageService storageService) {
        this.folderRepository = folderRepository;
        this.fileNodeRepository = fileNodeRepository;
        this.storageService = storageService;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod();
        switch (method) {
            case "OPTIONS" -> {
                resp.setHeader("DAV", "1,2");
                resp.setHeader("Allow", "OPTIONS, GET, PUT, DELETE, MKCOL, PROPFIND");
                resp.setStatus(200);
            }
            case "PROPFIND" -> propfind(req, resp);
            case "GET" -> get(req, resp);
            case "PUT" -> put(req, resp);
            case "DELETE" -> delete(req, resp);
            case "MKCOL" -> mkcol(req, resp);
            default -> resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private String pathOf(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String p = uri.replaceFirst("^/webdav/?", "");
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private ResolveResult resolve(String path) {
        if (path == null || path.isBlank()) {
            return new ResolveResult(folderRepository.findById("root").orElseThrow(), null, true);
        }
        String[] parts = path.split("/");
        Folder cur = folderRepository.findById("root").orElseThrow();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) continue;
            boolean last = i == parts.length - 1;
            Optional<Folder> folder = folderRepository.findFirstByFolderParentAndFolderName(cur.getFolderId(), part);
            if (folder.isPresent()) {
                cur = folder.get();
                if (last) return new ResolveResult(cur, null, true);
                continue;
            }
            if (last) {
                Optional<FileNode> file = fileNodeRepository.findByFileParentFolderAndFileName(cur.getFolderId(), part);
                if (file.isPresent()) return new ResolveResult(cur, file.get(), false);
            }
            return null;
        }
        return new ResolveResult(cur, null, true);
    }

    private record ResolveResult(Folder folder, FileNode file, boolean isFolder) {}

    private void propfind(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathOf(req);
        ResolveResult r = resolve(path);
        if (r == null) {
            resp.sendError(404);
            return;
        }
        resp.setStatus(207);
        resp.setContentType("application/xml;charset=UTF-8");
        PrintWriter w = resp.getWriter();
        w.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        w.println("<D:multistatus xmlns:D=\"DAV:\">");
        String hrefBase = "/webdav/" + (path.isBlank() ? "" : path + (r.isFolder ? "/" : ""));
        writeProp(w, hrefBase, r.isFolder, r.isFolder ? r.folder.getFolderName() : r.file.getFileName(),
                r.isFolder ? 0 : Long.parseLong(r.file.getFileSize()));
        if (r.isFolder) {
            List<Folder> folders = folderRepository.findByFolderParentOrderByFolderNameAsc(r.folder.getFolderId());
            for (Folder f : folders) {
                writeProp(w, "/webdav/" + join(path, f.getFolderName()) + "/", true, f.getFolderName(), 0);
            }
            List<FileNode> files = fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(r.folder.getFolderId());
            for (FileNode n : files) {
                writeProp(w, "/webdav/" + join(path, n.getFileName()), false, n.getFileName(), Long.parseLong(n.getFileSize()));
            }
        }
        w.println("</D:multistatus>");
    }

    private String join(String path, String name) {
        return path == null || path.isBlank() ? name : path + "/" + name;
    }

    private void writeProp(PrintWriter w, String href, boolean collection, String name, long size) {
        w.println("<D:response>");
        w.println("<D:href>" + href + "</D:href>");
        w.println("<D:propstat><D:prop>");
        w.println("<D:displayname>" + escape(name) + "</D:displayname>");
        if (collection) {
            w.println("<D:resourcetype><D:collection/></D:resourcetype>");
        } else {
            w.println("<D:resourcetype/>");
            w.println("<D:getcontentlength>" + size + "</D:getcontentlength>");
        }
        w.println("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>");
        w.println("</D:response>");
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ResolveResult r = resolve(pathOf(req));
        if (r == null) {
            resp.sendError(404);
            return;
        }
        if (r.isFolder) {
            // directory GET -> treat as PROPFIND listing for browsers
            propfind(req, resp);
            return;
        }
        var path = storageService.resolveBlock(r.file.getFilePath());
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Length", String.valueOf(Files.size(path)));
        try (InputStream in = Files.newInputStream(path); OutputStream out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private void put(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathOf(req);
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        ResolveResult parent = resolve(parentPath);
        if (parent == null || !parent.isFolder) {
            resp.sendError(409);
            return;
        }
        Optional<FileNode> exist = fileNodeRepository.findByFileParentFolderAndFileName(parent.folder.getFolderId(), name);
        String block = storageService.saveNewBlock(req.getInputStream());
        if (exist.isPresent()) {
            storageService.deleteBlock(exist.get().getFilePath());
            FileNode n = exist.get();
            n.setFilePath(block);
            n.setFileSize(String.valueOf(Files.size(storageService.resolveBlock(block))));
            n.setFileCreationDate(IdUtil.now());
            fileNodeRepository.save(n);
            resp.setStatus(204);
        } else {
            FileNode n = new FileNode();
            n.setFileId(IdUtil.uuid());
            n.setFileName(name);
            n.setFilePath(block);
            n.setFileSize(String.valueOf(Files.size(storageService.resolveBlock(block))));
            n.setFileParentFolder(parent.folder.getFolderId());
            n.setFileCreationDate(IdUtil.now());
            n.setFileCreator("webdav");
            fileNodeRepository.save(n);
            resp.setStatus(201);
        }
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ResolveResult r = resolve(pathOf(req));
        if (r == null) {
            resp.sendError(404);
            return;
        }
        if (r.isFolder) {
            if ("root".equals(r.folder.getFolderId())) {
                resp.sendError(403);
                return;
            }
            folderRepository.delete(r.folder);
        } else {
            storageService.deleteBlock(r.file.getFilePath());
            fileNodeRepository.delete(r.file);
        }
        resp.setStatus(204);
    }

    private void mkcol(HttpServletRequest req, HttpServletResponse resp) {
        String path = pathOf(req);
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        ResolveResult parent = resolve(parentPath);
        if (parent == null || !parent.isFolder) {
            resp.setStatus(409);
            return;
        }
        if (folderRepository.findFirstByFolderParentAndFolderName(parent.folder.getFolderId(), name).isPresent()) {
            resp.setStatus(405);
            return;
        }
        Folder f = new Folder();
        f.setFolderId(IdUtil.uuid());
        f.setFolderName(name);
        f.setFolderParent(parent.folder.getFolderId());
        f.setFolderConstraint(0);
        f.setFolderCreationDate(IdUtil.now());
        f.setFolderCreator("webdav");
        try {
            folderRepository.saveAndFlush(f);
            resp.setStatus(201);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            resp.setStatus(405);
        }
    }
}
