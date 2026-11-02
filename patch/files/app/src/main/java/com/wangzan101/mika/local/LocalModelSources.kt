package com.wangzan101.mika.local

import java.net.URLEncoder

/**
 * 下载地址。
 *
 * 两个用途:
 * 1. App 内下载时用
 * 2. **给用户复制到别的工具去下**(迅雷、IDM、电脑上下完传手机)——
 *    几个 G 的文件在 App 内下不一定顺,这条路径是刚需
 *
 * 国内直连 HuggingFace 大概率下不动,所以优先魔搭和 hf-mirror。
 * 魔搭的直链是"先问一次、再下":问一次拿到带 `auth_key` 的 CDN 直链,
 * **10-15 分钟过期**,所以不能缓存,复制给用户的地址也得是"每次现取"的。
 */
internal object LocalModelSources {

    /** 魔搭取直链:返回 302,响应体里有个 href 指向真正的 CDN 文件。 */
    fun modelScopeQuery(repo: String, fileName: String): String =
        "https://www.modelscope.cn/api/v1/models/$repo/repo?Revision=master&FilePath=" +
            URLEncoder.encode(fileName, "UTF-8")

    /** hf-mirror 直链:国内可达,可以直接复制给用户。 */
    fun mirror(repo: String, fileName: String): String =
        "https://hf-mirror.com/$repo/resolve/main/$fileName"

    /** HuggingFace 官方直链(境外环境用)。 */
    fun official(repo: String, fileName: String): String =
        "https://huggingface.co/$repo/resolve/main/$fileName"

    /**
     * 给用户复制的地址。
     *
     * 用 hf-mirror 而不是魔搭:魔搭的直链带 10 分钟就过期的 auth_key,
     * 复制出去过一会儿就废了。hf-mirror 的地址是长期有效的,适合拿去别的工具下。
     */
    fun copyableUrl(model: LocalModelCatalog.Model, file: LocalModelCatalog.File): String =
        mirror(model.hfRepo, file.name)

    /** 从 302 响应体里抠出 CDN 直链。 */
    fun extractCdnLink(body: String): String? {
        val m = Regex("""href=["']([^"']+)["']""").find(body)
        val link = m?.groupValues?.get(1)?.replace("&amp;", "&")
        return link?.takeIf { it.startsWith("http") }
    }
}
