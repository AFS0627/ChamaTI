package br.com.chamati.service;

/** Normaliza o nome enviado pelo cliente: sem caminho, sem caracteres de controle, até 255 caracteres. */
final class NomeArquivoAnexo {

    static final int TAMANHO_MAXIMO = 255;

    private NomeArquivoAnexo() {
    }

    static String normalizar(String original, TipoArquivoAnexo tipo) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("\\p{Cntrl}", "").trim();
        if (nome.isEmpty() || nome.equals(".") || nome.equals("..")) {
            return "anexo" + extensao(tipo);
        }
        if (nome.length() > TAMANHO_MAXIMO) {
            String ext = extensaoDe(nome);
            int limite = TAMANHO_MAXIMO - ext.length();
            nome = nome.substring(0, limite) + ext;
        }
        return nome;
    }

    private static String extensaoDe(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto > 0 && nome.length() - ponto <= 10 ? nome.substring(ponto) : "";
    }

    private static String extensao(TipoArquivoAnexo tipo) {
        return switch (tipo) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case PDF -> ".pdf";
        };
    }
}
