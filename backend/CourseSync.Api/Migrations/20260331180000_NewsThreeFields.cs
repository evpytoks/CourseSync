using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations;

public partial class NewsThreeFields : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.AddColumn<string>(
            name: "group_name",
            table: "news",
            type: "character varying(50)",
            maxLength: 50,
            nullable: false,
            defaultValue: "");

        migrationBuilder.AddColumn<string>(
            name: "section",
            table: "news",
            type: "character varying(50)",
            maxLength: 50,
            nullable: false,
            defaultValue: "");

        migrationBuilder.Sql(
            """
            UPDATE news AS n
            SET group_name = COALESCE(NULLIF(TRIM(g.name), ''), 'Группа')
            FROM groups AS g
            WHERE n.group_id = g."Id";
            """);

        migrationBuilder.Sql(
            """
            UPDATE news SET group_name = 'Группа' WHERE TRIM(group_name) = '';
            """);

        migrationBuilder.Sql(
            """
            UPDATE news
            SET section = TRIM(split_part(title, ' · ', 2))
            WHERE title LIKE '% · %' AND NULLIF(TRIM(split_part(title, ' · ', 2)), '') IS NOT NULL;
            """);

        migrationBuilder.Sql(
            """
            UPDATE news
            SET section = 'Новости'
            WHERE section IS NULL OR TRIM(section) = '';
            """);

        migrationBuilder.RenameColumn(
            name: "description",
            table: "news",
            newName: "detail");

        migrationBuilder.DropColumn(
            name: "title",
            table: "news");
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.AddColumn<string>(
            name: "title",
            table: "news",
            type: "character varying(50)",
            maxLength: 50,
            nullable: false,
            defaultValue: "");

        migrationBuilder.Sql(
            """
            UPDATE news
            SET title = LEFT(TRIM(group_name) || ' · ' || TRIM(section), 50);
            """);

        migrationBuilder.RenameColumn(
            name: "detail",
            table: "news",
            newName: "description");

        migrationBuilder.DropColumn(
            name: "group_name",
            table: "news");

        migrationBuilder.DropColumn(
            name: "section",
            table: "news");
    }
}
