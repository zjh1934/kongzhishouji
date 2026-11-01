package com.wangzan101.mika.local

import java.net.URLEncoder

/**
 * 下载地址。
 *
 * 国内直连 HuggingFace 大概率下不动,所以主源用魔搭。魔搭的直链是"先问一次、再下":
 * 问一次拿到一个带 `auth_key` 的 CDN 直链,**10-15 分钟就过期**,
 * 所以不能缓存,每次重下都要重新问;下大文件中途断线重连也必须重新问一次。
 *
 * 备选源 hf-mirror 是把 huggingface.co 换成镜像域名,没有取直链这一步,
 * 但速度波动大(实测同一文件 39-50 MB/s 与 0.46 MB/s 都出现过),只做兜底。
 */
internal object LocalModelSources {

    /** 魔搭取直链:返回 302,响应体里有个 href 指向真正的 CDN 文件。 */
    fun modelScopeQuery(repo: String, fileName: String): String =
        "https://www.modelscope.cn/api/v1/models/$repo/repo?Revision=master&FilePath=" +
            URLEncoder.encode(fileName, "UTF-8")

    /** hf-mirror 直链。 */
    fun mirror(repo: String, fileName: String): String =
        "https://hf-mirror.com/$repo/resolve/main/$fileName"

    /** HuggingFace 官方直链(境外环境用)。 */
    fun official(repo: String, fileName: String): String =
        "https://huggingface.co/$repo/resolve/main/$fileName"

    /**
     * 从 302 响应体里抠出 CDN 直链。
     * 魔搭把地址放在一段 HTML 的 href 里,前后可能有别的链接,所以取第一个 http 开头的。
     */
    fun extractCdnLink(body: String): String? {
        val m = Regex("""href=["']([^"']+)["']""").find(body)
        val link = m?.groupValues?.get(1)?.replace("&amp;", "&")
        return link?.takeIf { it.startsWith("http") }
    }
}
